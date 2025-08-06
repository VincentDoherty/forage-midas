package com.jpmc.midascore.foundation;

import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.model.Incentive;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.time.LocalDateTime;

@Component
public class TransactionListener {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Autowired
    private RestTemplate restTemplate;

    @KafkaListener(topics = "${general.kafka-topic}", groupId = "midascore-group")
    public void listen(Transaction transaction) {
        Optional<UserRecord> senderOpt = userRepository.findById(transaction.getSenderId());
        Optional<UserRecord> recipientOpt = userRepository.findById(transaction.getRecipientId());

        if (senderOpt.isPresent() && recipientOpt.isPresent()) {
            UserRecord sender = senderOpt.get();
            UserRecord recipient = recipientOpt.get();
            System.out.printf("Received transaction: $%.2f from %s to %s%n",
                    transaction.getAmount(),
                    sender.getName(),
                    recipient.getName()
            );
        } else {
            System.out.println("Received transaction with missing user(s): " + transaction);
        }

        // No further action yet
        UserRecord sender = userRepository.findById(transaction.getSenderId()).orElse(null);
        UserRecord recipient = userRepository.findById(transaction.getRecipientId()).orElse(null);

        if (sender == null || recipient == null) return;
        if (sender.getBalance() < transaction.getAmount()) return;

        // Call incentive API
        Incentive incentiveResponse = restTemplate.postForObject(
                "http://localhost:8080/incentive",
                transaction,
                Incentive.class
        );

// Extract incentive amount
        double incentiveAmount = incentiveResponse != null ? incentiveResponse.getAmount() : 0.0;

        sender.setBalance(sender.getBalance() - transaction.getAmount());
        recipient.setBalance(recipient.getBalance() + transaction.getAmount());

        TransactionRecord record = new TransactionRecord();
        record.setSender(sender);
        record.setRecipient(recipient);
        record.setAmount(transaction.getAmount());
        record.setTimestamp(LocalDateTime.now());

        userRepository.save(sender);
        userRepository.save(recipient);
        transactionRecordRepository.save(record);
        // Print balances after transaction
        System.out.printf("Updated balances -> %s: %.2f, %s: %.2f%n",
                sender.getName(), sender.getBalance(),
                recipient.getName(), recipient.getBalance());
    }
}
