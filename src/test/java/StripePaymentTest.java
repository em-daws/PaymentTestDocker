import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StripePaymentTest {

    private static final String WIREMOCK_URL = "http://localhost:8080";

    @BeforeAll
    public static void setup() throws IOException {
        RestAssured.baseURI = WIREMOCK_URL;

        uploadStub("stripe_success.json");
        uploadStub("stripe_declined.json");
        uploadStub("stripe_timeout.json");
        uploadStub("stripe_503.json");
    }

    private static void uploadStub(String fileName) throws IOException {
        String stubJson = new String(Files.readAllBytes(
                Paths.get("src/test/resources/mappings/" + fileName)));

        given()
                .contentType(ContentType.JSON)
                .body(stubJson)
                .when()
                .post("/__admin/mappings")
                .then()
                .statusCode(201);
    }

    @Test
    public void stripeSuccessTest() {
        given()
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post("/v1/payment_intents")
                .then()
                .log().all()
                .statusCode(200)
                .body("id", equalTo("pi_mock_001"))
                .body("status", equalTo("requires_capture"))
                .body("amount", equalTo(15000))
                .body("currency", equalTo("usd"));
    }

    @Test
    public void stripeDeclinedTest() {
        given()
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post("/v1/payment_intents/declined")
                .then()
                .log().all()
                .statusCode(402)
                .body("error.code", equalTo("card_declined"))
                .body("error.decline_code", equalTo("insufficient_funds"))
                .body("error.message", equalTo("Your card has insufficient funds."));
    }

    @Test
    public void stripeServerErrorTest() {
        given()
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post("/v1/payment_intents/error")
                .then()
                .log().all()
                .statusCode(503)
                .body("error.type", equalTo("api_error"))
                .body("error.message", equalTo("Service unavailable"));
    }

    @Test
    public void stripeTimeoutTest() {
        io.restassured.config.RestAssuredConfig config = RestAssured.config()
                .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                        .setParam("http.socket.timeout", 12000)
                        .setParam("http.connection.timeout", 12000));

        long startTime = System.currentTimeMillis();

        given()
                .log().all()
                .config(config)
                .contentType(ContentType.JSON)
                .when()
                .post("/v1/payment_intents/timeout")
                .then()
                .log().all()
                .statusCode(200)
                .body("id", equalTo("pi_mock_timeout"))
                .body("status", equalTo("requires_capture"));

        long endTime = System.currentTimeMillis();
        long durationSeconds = (endTime - startTime) / 1000;

        assertTrue(durationSeconds >= 10,
                "Response time is less than " + durationSeconds + " sec"
        );
    }

}
