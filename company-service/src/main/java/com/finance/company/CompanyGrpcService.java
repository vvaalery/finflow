package com.finance.company;

import com.finance.company.grpc.AccountOwnershipRequest;
import com.finance.company.grpc.AccountOwnershipResponse;
import com.finance.company.grpc.CompanyServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CompanyGrpcService extends CompanyServiceGrpc.CompanyServiceImplBase {

    private static final Logger logger =
            Logger.getLogger(CompanyGrpcService.class.getName());

    private final DatabaseConfig database = new DatabaseConfig();

    @Override
    public void validateAccountOwnership(
            AccountOwnershipRequest request,
            StreamObserver<AccountOwnershipResponse> responseObserver
    ) {
        try {
            UUID accountId = UUID.fromString(request.getAccountId());
            UUID companyId = UUID.fromString(request.getCompanyId());

            String sql = """
                    SELECT
                        EXISTS (
                            SELECT 1
                            FROM accounts
                            WHERE id = ?
                        ) AS account_exists,
                        EXISTS (
                            SELECT 1
                            FROM accounts
                            WHERE id = ?
                              AND company_id = ?
                        ) AS belongs_to_company
                    """;

            boolean accountExists;
            boolean belongs;

            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setObject(1, accountId);
                statement.setObject(2, accountId);
                statement.setObject(3, companyId);

                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    accountExists = result.getBoolean("account_exists");
                    belongs = result.getBoolean("belongs_to_company");
                }
            }

            logger.info("Account ownership checked: accountId=" + accountId
                    + ", companyId=" + companyId
                    + ", accountExists=" + accountExists
                    + ", belongs=" + belongs);

            AccountOwnershipResponse response = AccountOwnershipResponse.newBuilder()
                    .setAccountExists(accountExists)
                    .setBelongsToCompany(belongs)
                    .setMessage(belongs
                            ? "Счёт принадлежит указанной компании"
                            : accountExists
                                    ? "Счёт принадлежит другой компании"
                                    : "Счёт не существует")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Некорректный UUID счёта или компании")
                            .asRuntimeException()
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Company Service database error", e);
            responseObserver.onError(
                    Status.UNAVAILABLE
                            .withDescription("Не удалось проверить счёт")
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }
}
