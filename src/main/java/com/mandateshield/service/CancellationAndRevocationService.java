package com.mandateshield.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CancellationAndRevocationService {

    public String getPlaybook(String rail) {
        return switch (rail != null ? rail.toUpperCase() : "") {
            case "UPI_AUTOPAY" -> "1. Open your UPI app (GPay, PhonePe, Paytm). 2. Go to 'AutoPay' or 'Mandates' section. 3. Select the mandate. 4. Click 'Pause' or 'Revoke/Cancel'.";
            case "NACH" -> "1. Visit your bank's net banking portal. 2. Navigate to 'Services' > 'NACH Mandates' or 'e-Mandates'. 3. Locate the active mandate. 4. Select 'Cancel' or 'Stop'. 5. Alternatively, submit a physical cancellation form at your home branch.";
            case "CREDIT_CARD" -> "1. Log into your credit card app or net banking. 2. Go to 'Manage Cards' > 'Standing Instructions' (SI) or 'Recurring Payments'. 3. Find the specific merchant. 4. Delete the SI. 5. Alternatively, contact customer care to block the specific merchant.";
            default -> "Contact your bank or payment provider for specific cancellation instructions for this payment rail.";
        };
    }

    public CancellationPlaybook getPlaybookDetails(String rail, String merchant) {
        if (merchant != null && merchant.toLowerCase().contains("netflix")) {
            return new CancellationPlaybook("Netflix", List.of(
                    "Open Netflix Manage Membership and sign in.",
                    "Choose Cancel, then Finish Cancellation.",
                    "Keep the confirmation email and check the next billing date.",
                    "If the cancel option is missing, open the Netflix account page and follow its payment-partner instructions."),
                    List.of("If billed through a partner, cancel with that payment partner too."),
                    List.of("If billing continues, contact Netflix support and your payment provider with the confirmation and transaction references."),
                    "https://help.netflix.com/en/node/407", LocalDate.now().toString());
        }
        String normalized = rail == null ? "" : rail.toUpperCase();
        return switch (normalized) {
            case "UPI_AUTOPAY", "UPI" -> new CancellationPlaybook("UPI AutoPay", List.of(
                    "Open the UPI app that manages this mandate.", "Open Manage bank accounts or UPI AutoPay / Mandates.",
                    "Select the matching merchant and mandate, then choose pause or revoke.",
                    "Save the confirmation and verify the mandate status."),
                    List.of("Cancel the merchant plan as well if you want to end the service."),
                    List.of("If the mandate remains active, contact your UPI app or bank and provide the mandate reference."),
                    "https://www.npci.org.in/uploads/UPI_OC_223_Enhancement_of_UPI_Autopay_3e6a1cdb33.pdf", LocalDate.now().toString());
            case "NACH" -> new CancellationPlaybook("NACH / bank debit", List.of(
                    "Contact your bank through its official app, branch, or support number.",
                    "Ask to withdraw the specific NACH mandate or place a stop instruction; provide the mandate reference.",
                    "Ask for written confirmation and the effective date."),
                    List.of("Send a cancellation request to the merchant too, so the service and mandate are both addressed."),
                    List.of("If debits continue after your bank confirms withdrawal, raise a written dispute with the bank and merchant and retain the case numbers."),
                    "https://www.rbi.org.in/CommonPerson/english/scripts/Notification.aspx?Id=984", LocalDate.now().toString());
            case "CREDIT_CARD", "DEBIT_CARD" -> new CancellationPlaybook("Card standing instruction", List.of(
                    "Open your card issuer's official app or website and find Standing Instructions / Recurring Payments.",
                    "Select the matching merchant instruction and disable or cancel it.",
                    "Save the confirmation and check the next statement."),
                    List.of("Cancel the service with the merchant as well; removing a card instruction may not close the account."),
                    List.of("If the instruction is not listed or a debit continues, contact the card issuer using the number on your card and request a recurring-payment dispute."),
                    "https://m.rbi.org.in/scripts/RTGS_Notification.aspx?Id=11668", LocalDate.now().toString());
            default -> new CancellationPlaybook("Payment provider", List.of(
                    "Check the app or bank that authorized this recurring payment for a Mandates, AutoPay, or Standing Instructions section.",
                    "Select the matching merchant and ask the payment provider how to revoke it.",
                    "Save written confirmation and monitor the next statement."),
                    List.of("Also cancel directly with the merchant."),
                    List.of("If a future payment still appears, contact your payment provider promptly with the transaction and confirmation references."),
                    "https://m.rbi.org.in/scripts/RTGS_Notification.aspx?Id=11668", LocalDate.now().toString());
        };
    }

    public record CancellationPlaybook(String route, List<String> paymentSteps, List<String> merchantSteps,
                                       List<String> ifItContinues, String sourceUrl, String reviewedOn) {}

    public String generateRevocationLetter(String userName, String bankName, String merchantName, String mandateRef) {
        return """
            To,
            The Branch Manager, %s
            
            SUBJECT: NOTICE OF REVOCATION AND CANCELLATION OF MANDATE FOR %s
            
            Dear Sir/Madam,
            
            I, %s, hereby formally revoke and withdraw consent for the recurring payment mandate 
            registered under Reference/Descriptor: %s in favor of %s with immediate effect.
            
            Please acknowledge this request in writing and confirm the mandate withdrawal and its effective date.
            
            Date: %s
            Sincerely,
            %s
            """.formatted(bankName, merchantName.toUpperCase(), userName, mandateRef, merchantName, LocalDate.now(), userName);
    }
}
