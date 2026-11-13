package br.dev.ispchat.principal.service;

import br.dev.ispchat.grpc.DirectoryServiceGrpc;
import br.dev.ispchat.grpc.SearchCnpjRequest;
import br.dev.ispchat.grpc.SearchCnpjResponse;
import br.dev.ispchat.grpc.VerificationCredential;
import br.dev.ispchat.grpc.VerifiedCorporateEntry;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Diretorio publico exclusivo de contas corporativas CNPJ verificadas e ativas.
 *
 * Restricao estrita de privacidade: NENHUMA conta individual CPF e indexada ou retornada
 * por este servico (doc_type = 'CNPJ' AND status = 'ACTIVE' fixo na query parametrizada).
 */
@Service
public class DirectoryGrpcService extends DirectoryServiceGrpc.DirectoryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DirectoryGrpcService.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    public DirectoryGrpcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void searchVerifiedCnpj(
            SearchCnpjRequest request,
            StreamObserver<SearchCnpjResponse> responseObserver) {
        try {
            int limit = request.getLimit() <= 0 ? DEFAULT_LIMIT : Math.min(MAX_LIMIT, request.getLimit());
            String rawQuery = request.getQuery() != null ? request.getQuery().trim() : "";

            List<VerifiedCorporateEntry> entries;
            if (rawQuery.isEmpty()) {
                entries = jdbcTemplate.query("""
                        SELECT user_uuid, public_cnpj, public_display_name, home_node_address,
                               root_ed25519_pub, root_x25519_pub, credential_bytes
                          FROM verified_accounts
                         WHERE status = 'ACTIVE'
                           AND doc_type = 'CNPJ'
                         ORDER BY public_display_name ASC
                         LIMIT ?
                        """,
                        (rs, rowNum) -> mapEntry(rs),
                        limit);
            } else {
                String cleanedCnpjToken = stripFormatting(rawQuery).toUpperCase(Locale.ROOT);
                String namePattern = "%" + escapeLike(rawQuery) + "%";
                String cnpjPattern = "%" + escapeLike(cleanedCnpjToken) + "%";

                entries = jdbcTemplate.query("""
                        SELECT user_uuid, public_cnpj, public_display_name, home_node_address,
                               root_ed25519_pub, root_x25519_pub, credential_bytes
                          FROM verified_accounts
                         WHERE status = 'ACTIVE'
                           AND doc_type = 'CNPJ'
                           AND (public_display_name ILIKE ? ESCAPE '\\'
                                OR public_cnpj ILIKE ? ESCAPE '\\')
                         ORDER BY public_display_name ASC
                         LIMIT ?
                        """,
                        (rs, rowNum) -> mapEntry(rs),
                        namePattern,
                        cnpjPattern,
                        limit);
            }

            responseObserver.onNext(SearchCnpjResponse.newBuilder()
                    .addAllEntries(entries)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Falha em DirectoryGrpcService.searchVerifiedCnpj", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Erro ao consultar diretorio corporativo CNPJ")
                    .asRuntimeException());
        }
    }

    private static VerifiedCorporateEntry mapEntry(java.sql.ResultSet rs) throws SQLException {
        try {
            VerificationCredential credential = VerificationCredential.parseFrom(rs.getBytes("credential_bytes"));
            return VerifiedCorporateEntry.newBuilder()
                    .setCorporateUuid(rs.getObject("user_uuid", UUID.class).toString())
                    .setCnpj(rs.getString("public_cnpj"))
                    .setDisplayName(rs.getString("public_display_name"))
                    .setHomeNodeAddress(rs.getString("home_node_address"))
                    .setRootEd25519Pub(ByteString.copyFrom(rs.getBytes("root_ed25519_pub")))
                    .setRootX25519Pub(ByteString.copyFrom(rs.getBytes("root_x25519_pub")))
                    .setVerificationCredential(credential)
                    .build();
        } catch (InvalidProtocolBufferException e) {
            throw new SQLException("Credencial corrompida em verified_accounts", e);
        }
    }

    private static String stripFormatting(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '.' && c != '-' && c != '/' && !Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
