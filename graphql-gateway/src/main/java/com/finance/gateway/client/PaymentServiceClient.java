package com.finance.gateway.client;

import com.finance.graphqlapi.dto.GraphQLInput.*;
import com.finance.graphqlapi.dto.GraphQLView.*;

import java.util.List;
import java.util.UUID;

public interface PaymentServiceClient {
    List<PaymentView> payments(UUID companyId);
    MutationResult createPayment(PaymentInput input);
    MutationResult updatePayment(UUID id, PaymentInput input);
    MutationResult deletePayment(UUID id);

    List<PaymentRequestView> paymentRequests();
    PaymentRequestView paymentRequest(UUID id);
    MutationResult createPaymentRequest(PaymentRequestInput input);
    MutationResult approvePaymentRequest(UUID id);
    MutationResult rejectPaymentRequest(UUID id);
}
