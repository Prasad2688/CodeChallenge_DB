package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.TransferRequest;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class AccountsService {

    private static final String DEBIT_MESSAGE = "Your account is debited with ";

    private static final String CREDIT_MESSAGE = "Your account is credited with ";
    @Getter
    private final AccountsRepository accountsRepository;

    @Autowired
    private EmailNotificationService emailNotificationService;

    @Autowired
    public AccountsService(AccountsRepository accountsRepository) {
        this.accountsRepository = accountsRepository;
    }

    public void createAccount(Account account) {
        this.accountsRepository.createAccount(account);
    }

    public Account getAccount(String accountId) {
        return this.accountsRepository.getAccount(accountId);
    }

    /**
     * This service method will perform transfer of funds between two accounts
     *
     * @param transferRequest - Money transfer request with details as from account, to account
     *                        and money to be transferred
     */
    public void transferFunds(TransferRequest transferRequest) {
        //Check both account ids provided are valid
        Account fromAccount = Optional.ofNullable(this.accountsRepository.getAccount(transferRequest.getFromAccountId()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid source account ID"));
        Account toAccount = Optional.ofNullable(this.accountsRepository.getAccount(transferRequest.getToAccountId()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid destination account ID"));
        log.debug("Source and destination accounts provided in the money transfer request are valid.");
        // Ensure accounts are locked in a consistent order to avoid deadlock
        Account firstAccountToBeLocked = transferRequest.getFromAccountId().compareTo(transferRequest.getToAccountId()) < 0
                ? fromAccount : toAccount;
        Account secondAccountToBeLocked = transferRequest.getFromAccountId().compareTo(transferRequest.getToAccountId()) < 0
                ? toAccount : fromAccount;

        //Execute money transfer in synchronized manner
        synchronized (firstAccountToBeLocked) {
            synchronized (secondAccountToBeLocked) {
                BigDecimal amountToBeTransferred = transferRequest.getAmount();
                if (fromAccount.withdraw(amountToBeTransferred)) {
                    //If withdraw is successful then process deposit to destination account
                    toAccount.deposit(amountToBeTransferred);
                    //Update repository with new states of both accounts
                    this.accountsRepository.updateAccount(fromAccount);
                    this.accountsRepository.updateAccount(toAccount);
                    //Send notification to both account holders in async way
                    CompletableFuture.runAsync(() -> emailNotificationService.notifyAboutTransfer(fromAccount
                            , DEBIT_MESSAGE.concat(String.valueOf(amountToBeTransferred))));
                    CompletableFuture.runAsync(() -> emailNotificationService.notifyAboutTransfer(toAccount
                            , CREDIT_MESSAGE.concat(String.valueOf(amountToBeTransferred))));
                } else {
                    log.error("Insufficient funds in the account {} for withdrawal of amount {}",
                            fromAccount, amountToBeTransferred);
                    throw new IllegalArgumentException("Insufficient funds in the account for requested money transfer.");
                }
            }
        }
    }
}
