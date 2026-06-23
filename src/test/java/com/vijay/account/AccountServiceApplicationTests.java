package com.vijay.account;

import com.vijay.account.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class AccountServiceApplicationTests {

	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountTransactionRepository accountTransactionRepository;

	@BeforeEach
	void setUp() {
		accountTransactionRepository.deleteAll();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void postTransactionCreatesTransaction() throws Exception {
		mockMvc.perform(post("/accounts/account-1/transactions")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transactionJson("event-1", "CREDIT", "100.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventId").value("event-1"))
				.andExpect(jsonPath("$.accountId").value("account-1"))
				.andExpect(jsonPath("$.type").value("CREDIT"))
				.andExpect(jsonPath("$.amount").value(100.00))
				.andExpect(jsonPath("$.currency").value("USD"))
				.andExpect(jsonPath("$.status").value("APPLIED"));

		mockMvc.perform(get("/metrics"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalTransactionCount").value(1));
	}

	@Test
	void duplicateEventIdDoesNotCreateDuplicateTransaction() throws Exception {
		String requestBody = transactionJson("event-duplicate", "CREDIT", "100.00", "2026-06-22T10:00:00Z");

		mockMvc.perform(post("/accounts/account-1/transactions")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("APPLIED"));

		mockMvc.perform(post("/accounts/account-1/transactions")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DUPLICATE"));

		mockMvc.perform(get("/metrics"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalTransactionCount").value(1))
				.andExpect(jsonPath("$.creditTransactionCount").value(1));
	}

	@Test
	void balanceEqualsCreditMinusDebit() throws Exception {
		createTransaction("account-balance", "credit-1", "CREDIT", "150.00", "2026-06-22T10:00:00Z");
		createTransaction("account-balance", "debit-1", "DEBIT", "40.00", "2026-06-22T11:00:00Z");

		mockMvc.perform(get("/accounts/account-balance/balance"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value("account-balance"))
				.andExpect(jsonPath("$.balance").value(110.00))
				.andExpect(jsonPath("$.currency").value("USD"));
	}

	@Test
	void outOfOrderEventTimestampsAreReturnedByRecentTimestampDescending() throws Exception {
		createTransaction("account-order", "event-newer", "CREDIT", "20.00", "2026-06-22T12:00:00Z");
		createTransaction("account-order", "event-older", "DEBIT", "5.00", "2026-06-22T09:00:00Z");

		mockMvc.perform(get("/accounts/account-order"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recentTransactions", hasSize(2)))
				.andExpect(jsonPath("$.recentTransactions[0].eventId").value("event-newer"))
				.andExpect(jsonPath("$.recentTransactions[0].eventTimestamp").value("2026-06-22T12:00:00Z"))
				.andExpect(jsonPath("$.recentTransactions[1].eventId").value("event-older"))
				.andExpect(jsonPath("$.recentTransactions[1].eventTimestamp").value("2026-06-22T09:00:00Z"));
	}

	@Test
	void validationRejectsMissingFields() throws Exception {
		mockMvc.perform(post("/accounts/account-1/transactions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.path").value("/accounts/account-1/transactions"));
	}

	@Test
	void validationRejectsZeroAmount() throws Exception {
		mockMvc.perform(post("/accounts/account-1/transactions")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transactionJson("event-zero", "CREDIT", "0.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void validationRejectsNegativeAmount() throws Exception {
		mockMvc.perform(post("/accounts/account-1/transactions")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transactionJson("event-negative", "CREDIT", "-1.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void traceIdRequestHeaderIsReturnedInResponse() throws Exception {
		mockMvc.perform(post("/accounts/account-trace/transactions")
						.header(TRACE_ID_HEADER, "trace-test-123")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transactionJson("event-trace", "CREDIT", "10.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isCreated())
				.andExpect(header().string(TRACE_ID_HEADER, "trace-test-123"))
				.andExpect(jsonPath("$.traceId").value("trace-test-123"));
	}

	@Test
	void healthReturnsUp() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"));
	}

	@Test
	void metricsReturnsTransactionCounts() throws Exception {
		createTransaction("account-1", "metric-credit-1", "CREDIT", "50.00", "2026-06-22T10:00:00Z");
		createTransaction("account-1", "metric-debit-1", "DEBIT", "10.00", "2026-06-22T11:00:00Z");
		createTransaction("account-2", "metric-credit-2", "CREDIT", "25.00", "2026-06-22T12:00:00Z");

		mockMvc.perform(get("/metrics"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serviceName").value("account-service"))
				.andExpect(jsonPath("$.totalTransactionCount").value(3))
				.andExpect(jsonPath("$.creditTransactionCount").value(2))
				.andExpect(jsonPath("$.debitTransactionCount").value(1))
				.andExpect(jsonPath("$.uniqueAccountCount").value(2));
	}

	private void createTransaction(
			String accountId,
			String eventId,
			String type,
			String amount,
			String eventTimestamp
	) throws Exception {
		mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(transactionJson(eventId, type, amount, eventTimestamp)))
				.andExpect(status().isCreated());
	}

	private String transactionJson(String eventId, String type, String amount, String eventTimestamp) {
		return """
				{
				  "eventId": "%s",
				  "type": "%s",
				  "amount": %s,
				  "currency": "USD",
				  "eventTimestamp": "%s"
				}
				""".formatted(eventId, type, amount, eventTimestamp);
	}
}
