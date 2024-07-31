package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.TransferRequest;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.repository.AccountsRepository;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AccountsServiceTest {

    @InjectMocks
    private AccountsService accountsService;

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    private Account fromAccount;
    private Account toAccount;
    private TransferRequest transferRequest;

    @BeforeEach
    void setUp() {
        fromAccount = new Account("1", new BigDecimal("1000"));
        toAccount = new Account("2", new BigDecimal("500"));
        transferRequest = new TransferRequest("1", "2", new BigDecimal("200"));
    }

    @Test
    void addAccount() {
        Account account = new Account("Id-123");
        account.setBalance(new BigDecimal(1000));
        this.accountsService.createAccount(account);

        assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
    }

    @Test
    void addAccount_failsOnDuplicateId() {
        String uniqueId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueId);
        this.accountsService.createAccount(account);

        try {
            this.accountsService.createAccount(account);
            fail("Should have failed when adding duplicate account");
        } catch (DuplicateAccountIdException ex) {
            assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
        }
    }

    @Test
    void testTransferFundsSuccessful() {
        BigDecimal initialBalanceInFromAccount = fromAccount.getBalance();
        BigDecimal initialBalanceInToAccount = toAccount.getBalance();
        when(accountsRepository.getAccount("1")).thenReturn(fromAccount);
        when(accountsRepository.getAccount("2")).thenReturn(toAccount);
        doNothing().when(accountsRepository).updateAccount(any(Account.class));
        doNothing().when(emailNotificationService).notifyAboutTransfer(any(Account.class), any(String.class));

        // Capture the arguments
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);

        accountsService.transferFunds(transferRequest);

        verify(accountsRepository).getAccount("1");
        verify(accountsRepository).getAccount("2");
        verify(accountsRepository, times(2)).updateAccount(accountCaptor.capture());
        List<Account> capturedAccounts = accountCaptor.getAllValues();
        assertEquals(2, capturedAccounts.size());
        assertTrue(capturedAccounts.contains(fromAccount));
        assertTrue(capturedAccounts.contains(toAccount));
        // Verify the amounts
        assertEquals(initialBalanceInFromAccount.subtract(transferRequest.getAmount()), capturedAccounts.stream()
                .filter(a -> a.getAccountId().equals("1")).findFirst().get().getBalance());
        assertEquals(initialBalanceInToAccount.add(transferRequest.getAmount()), capturedAccounts.stream()
                .filter(a -> a.getAccountId().equals("2")).findFirst().get().getBalance());
    }

    @Test
    void testTransferFundsInsufficientFunds() {
        transferRequest = new TransferRequest("1", "2", new BigDecimal("2000"));
        when(accountsRepository.getAccount("1")).thenReturn(fromAccount);
        when(accountsRepository.getAccount("2")).thenReturn(toAccount);
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            accountsService.transferFunds(transferRequest);
        });
        assertEquals("Insufficient funds in the account for requested money transfer.", exception.getMessage());
    }

    @Test
    void testTransferFundsInvalidFromAccount() {
        when(accountsRepository.getAccount("1")).thenReturn(null);
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            accountsService.transferFunds(transferRequest);
        });
        assertEquals("Invalid source account ID", exception.getMessage());
    }

    @Test
    void testTransferFundsInvalidToAccount() {
        when(accountsRepository.getAccount("1")).thenReturn(fromAccount);
        when(accountsRepository.getAccount("2")).thenReturn(null);
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            accountsService.transferFunds(transferRequest);
        });
        assertEquals("Invalid destination account ID", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 0.0})
    void testInvalidAmountWithdrawal(double invalidAmount) {
        when(accountsRepository.getAccount("1")).thenReturn(fromAccount);
        when(accountsRepository.getAccount("2")).thenReturn(toAccount);
        transferRequest.setAmount(BigDecimal.valueOf(invalidAmount));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            accountsService.transferFunds(transferRequest);
        });
        assertEquals("Amount to be withdrawn must be positive", exception.getMessage());
    }
}
