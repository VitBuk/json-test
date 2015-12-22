package lv.lpb.services;

import lv.lpb.Constants;
import lv.lpb.services.events.ReportSender;
import lv.lpb.services.events.ReportReceiver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import lv.lpb.database.DAOQualifier;
import lv.lpb.database.DAOQualifier.DaoType;
import lv.lpb.database.MerchantDAO;
import lv.lpb.database.TransactionDAO;
import lv.lpb.domain.CancelInfo;
import lv.lpb.domain.Currency;
import lv.lpb.domain.Merchant;
import lv.lpb.domain.Transaction;
import lv.lpb.rest.errorHandling.AppException;
import lv.lpb.rest.errorHandling.Errors;
import lv.lpb.rest.params.TransactionFilterParams;
import lv.lpb.services.ServiceQualifier.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
@ServiceQualifier(serviceType = ServiceType.TRAN)
@InterceptorQualifier
public class TransactionsService {

    private static final Logger log = LoggerFactory.getLogger(TransactionsService.class);

    private MerchantDAO merchantDAO;
    private TransactionDAO transactionDAO;
    private ReportSender reportSender;

    public TransactionsService() {
    }

    @Inject
    public TransactionsService(@DAOQualifier(daoType = DaoType.DATABASE) TransactionDAO transactionDAO,
            @DAOQualifier(daoType = DaoType.DATABASE) MerchantDAO merchantDAO, ReportSender reportSender, ReportReceiver reportReceiver) {
        this.transactionDAO = transactionDAO;
        this.merchantDAO = merchantDAO;
        this.reportSender = reportSender;
    }

    public Transaction create(Long merchantId, Transaction transaction) {
        Merchant merchant = merchantDAO.get(merchantId);

        System.out.println("TransactionCreate merchant: " + merchant);
        if (merchant.getStatus() == Merchant.Status.INACTIVE) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.MERCH_INACTIVE);
        }
        if (!merchant.allowedCurrency(transaction.getCurrency())) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.UNALLOWED_CURRENCY);
        }

        transaction.setMerchant(merchant);
        transaction.setCreated(LocalDateTime.now());
        transactionDAO.create(transaction);

        return transaction;
    }

    public Transaction cancel(CancelInfo cancelInfo) {
        Transaction transaction = transactionDAO.get(cancelInfo.getTransactionId());

        if (transaction.getStatus() == Transaction.Status.DECLINED) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.CANCEL_DECLINED);
        }
        if (transaction.getStatus() == Transaction.Status.REVERSED && transaction.getAmount() == BigDecimal.ZERO) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.CANCEL_REVERSED);
        }

        if (transaction.getAmount().compareTo(cancelInfo.getAmount()) == -1) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.CANCEL_LIMIT_EXCESS);
        }
        if (cancelInfo.getAmount() == BigDecimal.ZERO) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.CANCEL_ZERO);
        }

        if (transaction.getCurrency() != cancelInfo.getCurrency()) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.CANCEL_WRONG_CURRENCY);
        }

        if (Period.between(transaction.getCreated().toLocalDate(), LocalDate.now()).getDays() > Constants.DAY_CANCEL_LIMIT) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.CANCEL_OVERDUE);
        }

        transaction.setAmount(transaction.getAmount().subtract(cancelInfo.getAmount()));
        transaction.setStatus(Transaction.Status.REVERSED);

        transactionDAO.update(transaction);
        transaction = transactionDAO.get(transaction.getId());

        return transaction;
    }

    public List<Transaction> getByMerchant(Map<String, Object> filterParams, Map<String, Object> pageParams) {
        List<Transaction> transactions = transactionDAO.getByParams(filterParams, pageParams);

        if (merchantDAO.get((Long) filterParams.get(TransactionFilterParams.MERCHANT_ID)) == null) {
            throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), Errors.MERCH_NOT_EXIST);
        }

        return transactions;
    }

    @Schedule(dayOfWeek = "*", hour = "0", minute = "0", second = "1")
    public void dayTotalAmount() {
        List<Transaction> transactions = new CopyOnWriteArrayList<>();
        for (Transaction transaction : transactionDAO.lastDayTransactions()) {
            transactions.add(transaction);
        }

        Map<Currency, BigDecimal> totalAmountMap = new HashMap<>();
        for (Currency currency : Currency.values()) {
            BigDecimal currencySum = BigDecimal.ZERO;
            for (Transaction transaction : transactions) {
                if (transaction.getCurrency() == currency) {
                    currencySum = currencySum.add(transaction.getAmount());
                    transactions.remove(transaction);
                }
            }
            totalAmountMap.put(currency, currencySum);
        }

        log.trace("Total day amount={} ", totalAmountMap);
        reportSender.send(totalAmountMap);
    }
}
