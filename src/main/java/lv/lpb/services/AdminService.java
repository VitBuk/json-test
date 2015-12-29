package lv.lpb.services;

import java.util.List;
import java.util.Map;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import lv.lpb.database.DAOQualifier;
import lv.lpb.database.DAOQualifier.DaoType;
import lv.lpb.database.MerchantDAO;
import lv.lpb.database.TransactionDAO;
import lv.lpb.domain.Exporter;
import lv.lpb.domain.Merchant;
import lv.lpb.domain.Transaction;
import lv.lpb.rest.errorHandling.AppException;
import lv.lpb.rest.errorHandling.Errors;
import lv.lpb.services.ServiceInterceptor.ProfileExecTime;

@Stateless
@ProfileExecTime
public class AdminService {

    private MerchantDAO merchantDAO;
    private TransactionDAO transactionDAO;

    public AdminService() {
    }

    @Inject
    public AdminService(@DAOQualifier(daoType = DaoType.DATABASE) MerchantDAO merchantDAO,
            @DAOQualifier(daoType = DaoType.DATABASE) TransactionDAO transactionDAO) {
        this.merchantDAO = merchantDAO;
        this.transactionDAO = transactionDAO;
    }

    public List<Merchant> getMerchants(Map<String, Object> filterParams, Map<String, Object> pageParams) {
        List<Merchant> merchants = merchantDAO.getByParams(filterParams, pageParams);

        if (merchants.isEmpty()) {
            throw new AppException(Response.Status.NO_CONTENT.getStatusCode(), Errors.MERCHS_ZERO);
        }

        return merchants;
    }

    public Merchant addMerchant(Merchant merchant) {
        merchant.setStatus(Merchant.Status.ACTIVE);
        merchantDAO.create(merchant);
        merchant = merchantDAO.get(merchant.getId());

        return merchant;
    }

    public Merchant switchOffMerchant(Long merchantId, Merchant.Status status) {
        Merchant merchant = merchantDAO.get(merchantId);
        merchant.setStatus(status);
        merchant = merchantDAO.update(merchant);

        return merchant;
    }

    public List<Transaction> getTransactions(Map<String, Object> filterParams,
            Map<String, Object> pageParams) {
        List<Transaction> transactions = transactionDAO.getByParams(filterParams, pageParams);

        if (transactions.isEmpty()) {
            throw new AppException(Response.Status.NO_CONTENT.getStatusCode(), Errors.TRANS_ZERO);
        }

        return transactions;
    }

    public Exporter exportMerchants() {
        List<Merchant> merchants = merchantDAO.getAll();
        Exporter exporter = new Exporter(merchants);

        return exporter;
    }

    public Exporter exportTransactions() {
        List<Transaction> transactions = transactionDAO.getAll();
        Exporter exporter = new Exporter(transactions);

        return exporter;
    }
}
