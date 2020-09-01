package ee.taltech.arete.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.taltech.arete.AreteApplication;
import ee.taltech.arete.api.data.request.AreteRequest;
import ee.taltech.arete.api.data.response.arete.AreteResponse;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import static ee.taltech.arete.initializers.SubmissionInitializer.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@AutoConfigureTestDatabase
@RunWith(SpringRunner.class)
@SpringBootTest(
		classes = AreteApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PythonIntegrationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@LocalServerPort
	private int port;

	@Before
	public void init() {
		RestAssured.defaultParser = Parser.JSON;
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = port;
	}

	@Test
	public void addNewSubmissionSyncNoTestsDoesntReturnTestFiles() {

		AreteResponse response = given()
				.when()
				.body(getFullSubmissionStringPythonSyncNoTesterFiles(String.format("http://localhost:%s", port)))
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		assertEquals(0, response.getTestFiles().size());
	}

	@Test
	public void addNewSubmissionSyncNoStdReturnsNoStd() {

		AreteResponse response = given()
				.when()
				.body(getFullSubmissionStringPythonSyncNoStdout(String.format("http://localhost:%s", port)))
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		assertEquals(0, response.getConsoleOutputs().size());
		assertFullSubmission(response);
		assertEquals(23, response.getTotalCount());
		assertEquals(16, response.getTotalPassedCount());

	}

	@Test
	public void addNewSubmissionSyncNoStyleReturnsStyle100() {

		AreteResponse response = given()
				.when()
				.body(getFullSubmissionStringPythonSyncNoStyle(String.format("http://localhost:%s", port)))
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		assertEquals(100, response.getStyle());
		assertFullSubmission(response);
		assertEquals(23, response.getTotalCount());
		assertEquals(16, response.getTotalPassedCount());
	}

	@Test
	public void addNewSubmissionSyncExamReturnsOutputAndReturnExtra() {
		ObjectNode root = objectMapper.createObjectNode();
		root.put("some", "stuff");
		AreteRequest submission = getFullSubmissionStringExamControllerEndpoint(String.format("http://localhost:%s", port));
		submission.setReturnExtra(root);

		JsonNode response = given()
				.when()
				.body(submission)
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(JsonNode.class);
		// then
		assertNotNull(response.get("output"));
		assertNotNull(response.get("returnExtra"));
	}

	@Test
	public void addNewSubmissionSyncExamReturnsFullSubmission() {

		AreteRequest payload = getFullSubmissionStringExamControllerEndpoint(String.format("http://localhost:%s", port));
		AreteResponse response = given()
				.when()
				.body(payload)
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		//then
		assertFullSubmission(response);
		assertEquals(55, response.getTotalCount());
		assertEquals(46, response.getTotalPassedCount());
		assertEquals("envomp@ttu.ee", response.getEmail());
	}

	@Test
	public void addNewSubmissionSyncPythonCustomConfigurationReturnsFullSubmission() {
		AreteRequest payload = getFullSubmissionStringControllerEndpointPythonCustomConfiguration(String.format("http://localhost:%s", port));
		AreteResponse response = given()
				.when()
				.body(payload)
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		//then
		assertFullSubmission(response);
		assertEquals(18, response.getTotalCount());
		assertEquals(18, response.getTotalPassedCount());
		assertEquals("envomp@ttu.ee", response.getEmail());
	}

	@Test
	public void addNewSubmissionSyncPythonRecursionReturnsOutputEmail() {
		AreteRequest payload = getFullSubmissionStringControllerEndpointPythonRecursion(String.format("http://localhost:%s", port));
		AreteResponse response = given()
				.when()
				.body(payload)
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		// then
		assertFullSubmission(response);
		assertEquals(1, response.getTotalCount());
		assertEquals(0, response.getTotalPassedCount());
		assertEquals("envomp@ttu.ee", response.getEmail());
	}

	@Test
	public void addNewSubmissionSyncPythonBigReturnsFullSubmission() {

		AreteRequest payload = getFullSubmissionStringControllerEndpointPythonLong(String.format("http://localhost:%s", port));
		AreteResponse response = given()
				.when()
				.body(payload)
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		// then
		assertFullSubmission(response);
		assertEquals(21, response.getTotalCount());
		assertEquals(13, response.getTotalPassedCount());
	}


	@Test
	public void addNewSubmissionSyncPythonReturnsFullSubmission() {

		AreteRequest payload = getFullSubmissionStringControllerEndpointPython(String.format("http://localhost:%s", port));
		AreteResponse response = given()
				.when()
				.body(payload)
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		// then
		assertFullSubmission(response);
		assertEquals(18, response.getTotalCount());
		assertEquals(18, response.getTotalPassedCount());
	}

	@Test
	public void addNewSubmissionSyncPythonFirstPushReturnsFullSubmission() {

		AreteRequest payload = getFullSubmissionStringControllerEndpointPythonFirstPush(String.format("http://localhost:%s", port));
		AreteResponse response = given()
				.when()
				.body(payload)
				.post(":testSync")
				.then()
				.statusCode(is(HttpStatus.SC_ACCEPTED))
				.extract()
				.body()
				.as(AreteResponse.class);

		// then
		assertFullSubmission(response);
		assertEquals(12, response.getTotalCount());
		assertEquals(12, response.getTotalPassedCount());
	}

}
