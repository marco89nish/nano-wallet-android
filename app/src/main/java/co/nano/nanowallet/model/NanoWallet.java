package co.nano.nanowallet.model;

import android.content.Context;

import com.hwangjr.rxbus.annotation.Subscribe;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import javax.inject.Inject;

import co.nano.nanowallet.bus.RxBus;
import co.nano.nanowallet.bus.SendInvalidAmount;
import co.nano.nanowallet.bus.WalletClear;
import co.nano.nanowallet.bus.WalletHistoryUpdate;
import co.nano.nanowallet.bus.WalletPriceUpdate;
import co.nano.nanowallet.bus.WalletSubscribeUpdate;
import co.nano.nanowallet.network.model.response.AccountHistoryResponse;
import co.nano.nanowallet.network.model.response.AccountHistoryResponseItem;
import co.nano.nanowallet.network.model.response.CurrentPriceResponse;
import co.nano.nanowallet.network.model.response.PendingTransactionResponseItem;
import co.nano.nanowallet.network.model.response.SubscribeResponse;
import co.nano.nanowallet.ui.common.ActivityWithComponent;
import co.nano.nanowallet.util.ExceptionHandler;
import co.nano.nanowallet.util.NumberUtil;
import co.nano.nanowallet.util.SharedPreferencesUtil;
import io.realm.Realm;


/**
 * Nano wallet that holds transactions and current prices
 */
public class NanoWallet {
    private BigDecimal accountBalance;
    private BigDecimal localCurrencyPrice;
    private BigDecimal btcPrice;

    private String accountAddress;
    private String representativeAddress;
    private String frontierBlock;
    private String openBlock;

    private Integer blockCount;

    private List<AccountHistoryResponseItem> accountHistory;

    private Queue<PendingTransactionResponseItem> pendingTransactions = new LinkedList<>();

    // for sending
    private String sendNanoAmount;
    private String sendLocalCurrencyAmount;

    @Inject
    SharedPreferencesUtil sharedPreferencesUtil;

    @Inject
    Realm realm;

    public NanoWallet(Context context) {
        // init dependency injection
        if (context instanceof ActivityWithComponent) {
            ((ActivityWithComponent) context).getActivityComponent().inject(this);
        }

        clear();
        RxBus.get().register(this);
    }

    public List<AccountHistoryResponseItem> getAccountHistory() {
        return accountHistory;
    }

    public String getAccountBalanceNano() {
        return NumberUtil.getRawAsUsableString(accountBalance.toString());
    }

    public BigDecimal getAccountBalanceNanoRaw() {
        return accountBalance;
    }

    public String getLongerAccountBalanceNano() {
        return NumberUtil.getRawAsLongerUsableString(accountBalance.toString());
    }

    public Queue<PendingTransactionResponseItem> getPendingTransactions() {
        return pendingTransactions;
    }

    public void setPendingTransactions(Queue<PendingTransactionResponseItem> pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
    }

    public String getAccountBalanceLocalCurrency() {
        return localCurrencyPrice != null && accountBalance != null ? formatLocalCurrency(NumberUtil.getRawAsUsableAmount(accountBalance.toString()).multiply(localCurrencyPrice, MathContext.DECIMAL64)) : "0.0";
    }

    public String getAccountBalanceBtc() {
        return btcPrice != null && accountBalance != null ? formatBtc(NumberUtil.getRawAsUsableAmount(accountBalance.toString()).multiply(btcPrice, MathContext.DECIMAL64)) : "0.0";
    }

    private String formatLocalCurrency(BigDecimal amount) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        return numberFormat.format(Double.valueOf(amount.toString()));
    }

    private String formatBtc(BigDecimal amount) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
        numberFormat.setMaximumFractionDigits(8);
        return numberFormat.format(Double.valueOf(amount.toString()));
    }

    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance;
    }

    public AvailableCurrency getLocalCurrency() {
        return sharedPreferencesUtil.getLocalCurrency();
    }

    public void clearSendAmounts() {
        sendNanoAmount = "";
        sendLocalCurrencyAmount = "";
    }

    /**
     * Get Nano amount entered
     *
     * @return String
     */
    public String getSendNanoAmount() {
        return sendNanoAmount;
    }

    /**
     * Return the currency formatted local currency amount as a string
     *
     * @return
     */
    public String getSendNanoAmountFormatted() {
        if (sendNanoAmount.length() > 0) {
            return nanoFormat(sendNanoAmount);
        } else {
            return "";
        }
    }


    /**
     * Set Nano amount which will also set the local currency amount
     *
     * @param nanoAmount String Nano Amount from input
     */
    public void setSendNanoAmount(String nanoAmount) {
        this.sendNanoAmount = nanoAmount;
        if (nanoAmount.length() > 0) {
            if (this.sendNanoAmount.equals(".")) {
                this.sendNanoAmount = "0.";
            }
            this.sendLocalCurrencyAmount = convertNanoToLocalCurrency(this.sendNanoAmount);
        } else {
            this.sendLocalCurrencyAmount = "";
        }
        validateSendAmount();
    }

    /**
     * Convert a Nano string amount to a local currency String
     *
     * @param amount String amount of Nano
     * @return String local currency amount
     */
    private String convertNanoToLocalCurrency(String amount) {
        if (amount.equals("0.")) {
            return amount;
        } else {
            return localCurrencyPrice != null ?
                    formatLocalCurrency(new BigDecimal(amount).multiply(localCurrencyPrice, MathContext.DECIMAL64))
                    : "0.0";
        }
    }

    /**
     * Return the  local currency amount as a string
     *
     * @return
     */
    public String getLocalCurrencyAmount() {
        return sendLocalCurrencyAmount;
    }

    /**
     * Return the currency formatted local currency amount as a string
     *
     * @return
     */
    public String getSendLocalCurrencyAmountFormatted() {
        if (sendLocalCurrencyAmount.length() > 0) {
            return currencyFormat(new BigDecimal(sendLocalCurrencyAmount));
        } else {
            return "";
        }
    }

    /**
     * Set the local currency amount and also update the nano amount to match
     *
     * @param localCurrencyAmount String of local currency amount from input
     */
    public void setLocalCurrencyAmount(String localCurrencyAmount) {
        this.sendLocalCurrencyAmount = localCurrencyAmount;
        if (localCurrencyAmount.length() > 0) {
            if (localCurrencyAmount.equals(".")) {
                this.sendLocalCurrencyAmount = "0.";
            }
            this.sendNanoAmount = convertLocalCurrencyToNano(this.sendLocalCurrencyAmount);
        } else {
            this.sendNanoAmount = "";
        }
        validateSendAmount();
    }

    /**
     * Convert a local currency string to a Nano string
     *
     * @param amount Local currency amount from input
     * @return String of Nano converted amount
     */
    private String convertLocalCurrencyToNano(String amount) {
        if (amount.equals("0.")) {
            return amount;
        } else {
            return localCurrencyPrice != null ? new BigDecimal(amount)
                    .divide(localCurrencyPrice, RoundingMode.FLOOR).toString() : "0.0";
        }
    }

    /**
     * Convert local currency to properly formatted string for the currency
     */
    private String currencyFormat(BigDecimal amount) {
        DecimalFormat df = new DecimalFormat("#,###.00");
        return df.format(amount);
    }

    /**
     * Convert local currency to properly formatted string for the currency
     */
    private String nanoFormat(String amount) {
        if (new BigDecimal(amount).doubleValue() == 0) {
            return amount;
        } else {
            DecimalFormat df = new DecimalFormat("#,###.##########");
            return df.format(new BigDecimal(amount));
        }
    }

    /**
     * Validate that the requested sned amount is not greater than the account balance
     */
    private void validateSendAmount() {
        try {
            new BigDecimal(sendNanoAmount);
            if (new BigDecimal(sendNanoAmount).compareTo(new BigDecimal(NumberUtil.getRawAsLongerUsableString(accountBalance.toString()))) > 0) {
                RxBus.get().post(new SendInvalidAmount());
            }
        } catch (NumberFormatException e) {
            ExceptionHandler.handle(e);
        }
    }

    public void close() {
        RxBus.get().unregister(this);
    }

    public void clear() {
        accountBalance = new BigDecimal("0.0");
        localCurrencyPrice = null;
        btcPrice = null;

        accountAddress = null;
        representativeAddress = null;
        frontierBlock = null;
        openBlock = null;

        blockCount = null;

        accountHistory = new ArrayList<>();

        // for sending
        sendNanoAmount = "";
        sendLocalCurrencyAmount = "";
    }

    public String getFrontierBlock() {
        if (frontierBlock == null && realm != null) {
            Credentials credentials = realm.where(Credentials.class).findFirst();
            return credentials.getPublicKey();
        } else {
            return frontierBlock;
        }
    }

    public void setFrontierBlock(String frontierBlock) {
        this.frontierBlock = frontierBlock;
    }

    /* Bus Listeners */

    /**
     * Receive account subscribe response
     *
     * @param subscribeResponse
     */
    @Subscribe
    public void receiveSubscribe(SubscribeResponse subscribeResponse) {
        frontierBlock = subscribeResponse.getFrontier();
        representativeAddress = subscribeResponse.getRepresentative_block();
        openBlock = subscribeResponse.getOpen_block();
        blockCount = subscribeResponse.getBlock_count();
        accountBalance = new BigDecimal(subscribeResponse.getBalance());
        RxBus.get().post(new WalletSubscribeUpdate());
    }

    /**
     * Receive a history update
     *
     * @param accountHistoryResponse
     */
    @Subscribe
    public void receiveHistory(AccountHistoryResponse accountHistoryResponse) {
        accountHistory = accountHistoryResponse.getHistory();
        RxBus.get().post(new WalletHistoryUpdate());
    }

    /**
     * Receive a current price response
     *
     * @param currentPriceResponse
     */
    @Subscribe
    public void receiveCurrentPrice(CurrentPriceResponse currentPriceResponse) {
        if (currentPriceResponse.getCurrency().equals("btc")) {
            // we made a btc price request
            btcPrice = new BigDecimal(currentPriceResponse.getPrice());
        } else {
            // local currency price
            localCurrencyPrice = new BigDecimal(currentPriceResponse.getPrice());
        }
        if (currentPriceResponse.getBtc() != null) {
            btcPrice = new BigDecimal(currentPriceResponse.getBtc());
        }
        RxBus.get().post(new WalletPriceUpdate());
    }

    /**
     * Receive clear wallet
     *
     * @param walletClear
     */
    @Subscribe
    public void receiveClear(WalletClear walletClear) {
        clear();
    }
}
