package com.smartparking.service;

import com.smartparking.repository.ConsentDao;

import java.util.List;

/**
 * Service managing user consents under India's Digital Personal Data Protection (DPDP) Act, 2023.
 */
public class ConsentService {

    private static final String DEFAULT_VERSION = "v1.0 (DPDP 2023)";
    private static volatile ConsentService instance;
    private final ConsentDao consentDao;
    private final AuditLogService auditLogService;

    public ConsentService(ConsentDao consentDao, AuditLogService auditLogService) {
        this.consentDao = consentDao;
        this.auditLogService = auditLogService;
    }

    public ConsentService() {
        this(new ConsentDao(), AuditLogService.getInstance());
    }

    public static ConsentService getInstance() {
        if (instance == null) {
            synchronized (ConsentService.class) {
                if (instance == null) {
                    instance = new ConsentService();
                }
            }
        }
        return instance;
    }

    public void recordConsent(String userIdOrPlate, String purpose, String textVersion) {
        consentDao.saveConsent(userIdOrPlate, purpose, textVersion != null ? textVersion : DEFAULT_VERSION);
        auditLogService.log("CONSENT_RECORDED", "DPDP_CONSENT", userIdOrPlate,
                null, purpose, "User gave statutory consent for: " + purpose, "SUCCESS");
    }

    public void recordConsent(String userIdOrPlate, String purpose) {
        recordConsent(userIdOrPlate, purpose, DEFAULT_VERSION);
    }

    public void withdrawConsent(String userIdOrPlate, String purpose) {
        consentDao.withdrawConsent(userIdOrPlate, purpose);
        auditLogService.log("CONSENT_WITHDRAWN", "DPDP_CONSENT", userIdOrPlate,
                "ACTIVE", "WITHDRAWN", "User withdrew statutory consent for: " + purpose, "SUCCESS");
    }

    public boolean hasValidConsent(String userIdOrPlate, String purpose) {
        return consentDao.hasActiveConsent(userIdOrPlate, purpose);
    }

    public List<ConsentDao.ConsentRecord> getConsents(String userIdOrPlate) {
        return consentDao.findByUserOrPlate(userIdOrPlate);
    }
}
