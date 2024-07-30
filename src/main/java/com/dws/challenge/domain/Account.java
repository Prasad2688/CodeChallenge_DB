package com.dws.challenge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Data
@Slf4j
public class Account {

    @NotNull
    @NotEmpty
    private final String accountId;

    @NotNull
    @Min(value = 0, message = "Initial balance must be positive.")
    private BigDecimal balance;

    public Account(String accountId) {
        this.accountId = accountId;
        this.balance = BigDecimal.ZERO;
    }

    @JsonCreator
    public Account(@JsonProperty("accountId") String accountId,
                   @JsonProperty("balance") BigDecimal balance) {
        this.accountId = accountId;
        this.balance = balance;
    }

    /**
     * This method will withdraw amount from account
     *
     * @param amount - Amount to be withdrawn
     * @return - true - if withdraw is successful
     * false - if withdraw is not possible
     */
    public boolean withdraw(BigDecimal amount) {
        //Check if amount to be withdrawn is positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Error:Triggered negative amount withdrawal on account {}", this.accountId);
            throw new IllegalArgumentException("Amount to be withdrawn must be positive");
        }
        //Check if balance in the account is not lesser than amount to be withdrawn
        if (this.balance.compareTo(amount) < 0) {
            log.error("Error:Overdraft is requested on account {}", this.accountId);
            return false;
        }
        //Withdraw amount from balance
        this.balance = this.balance.subtract(amount);
        log.info("Account with id {} is withdraw with amount {}", this.accountId, amount);
        return true;
    }

    /**
     * This method will deposit amount to account
     *
     * @param amount - Amount to be deposited
     */
    public void deposit(BigDecimal amount) {
        //Check if amount to be deposited is positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Error:Negative amount is tried to be deposited in account {}", this.accountId);
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.balance = this.balance.add(amount);
        log.info("Account with id {} is deposited with amount {}", this.accountId, amount);
    }
}
