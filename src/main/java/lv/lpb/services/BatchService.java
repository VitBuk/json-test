package lv.lpb.services;

import java.time.LocalDateTime;
import java.util.List;
import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import lv.lpb.database.BatchDAO;
import lv.lpb.database.DAO;
import lv.lpb.database.MerchantDAO;
import lv.lpb.database.TransactionDAO;
import lv.lpb.domain.Batch;
import lv.lpb.domain.Merchant;
import lv.lpb.domain.Transaction;
import lv.lpb.services.ServiceInterceptor.ProfileExecTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
@ProfileExecTime
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private BatchDAO batchDAO;
    private TransactionDAO transactionDAO;
    private MerchantDAO merchantDAO;

    public BatchService() {
    }

    @Inject
    public BatchService(@DAO BatchDAO batchDAO,
            @DAO TransactionDAO transactionDAO,
            @DAO MerchantDAO merchantDAO) {
        this.batchDAO = batchDAO;
        this.transactionDAO = transactionDAO;
        this.merchantDAO = merchantDAO;
    }

    @Schedule(dayOfWeek = "*", hour = "*", minute = "*", second = "00")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void closeBatch() {
        for (Merchant merchant : merchantDAO.getAll()) {
            create(merchant.getId());
        }
    }

    private Batch create(Long merchantId) {
        LocalDateTime batchDay = LocalDateTime.now().minusDays(1L);
        Merchant merchant = merchantDAO.get(merchantId);
        Batch batch = batchDAO.create(new Batch(merchant, batchDay));

        List<Transaction> transactions = transactionDAO.beforeToday();
        if (transactions.isEmpty()) {
            return null;
        }

        for (Transaction transaction : transactions) {
            transaction.setStatus(Transaction.Status.PROCESSED);
            transaction.setBatchId(batch.getId());
            batch.add(transaction);
        }

        batch = batchDAO.update(batch);
        log.trace("Batch={} for merchant={} ", batch, merchantId);

        return batch;
    }
}
