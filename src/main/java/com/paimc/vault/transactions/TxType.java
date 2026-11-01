package com.paimc.vault.transactions;

public enum TxType {
    PLAYER_PAY,
    ADMIN_SET,
    ADMIN_ADD,
    ADMIN_REMOVE,
    ADMIN_RESET,
    DEPOSIT,
    WITHDRAW,
    NOTE_WITHDRAW,
    NOTE_REDEEM,
    OFFLINE_PAY_SENT,
    OFFLINE_PAY_CLAIMED,
    OFFLINE_PAY_REFUNDED,
    BANK_DEPOSIT,
    BANK_WITHDRAW,
    INTEREST,
    TAX,
    // Legacy transaction identifiers retained only so pre-2.1.2 history can still be read.
    LOAN_DISBURSE,
    LOAN_REPAY,
    LOAN_DEFAULT,
    LOAN_COLLATERAL_SEIZED,
    TEAM_DEPOSIT,
    TEAM_WITHDRAW,
    TEAM_DISBAND_REFUND,
    CHARGE_PAID
}
