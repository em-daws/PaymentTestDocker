import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class StripePaymentTest {

    private static final String WIREMOCK_URL = "http://localhost:8080";

    @BeforeAll
    public static void setup() throws IOException {
        RestAssured.baseURI = WIREMOCK_URL;

        // Автоматически загружаем все ваши 4 стаба в Docker WireMock перед стартом тестов
        uploadStub("stripe_success.json");
        uploadStub("stripe_declined.json");
        uploadStub("stripe_timeout.json");
        uploadStub("stripe_503.json");
    }

    private static void uploadStub(String fileName) throws IOException {
        // Читаем JSON-файл из папки ресурсов проекта
        String stubJson = new String(Files.readAllBytes(
                Paths.get("src/test/resources/mappings/" + fileName)));

        // Отправляем стаб в админку WireMock
        given()
                .contentType(ContentType.JSON)
                .body(stubJson)
                .when()
                .post("/__admin/mappings")
                .then()
                .statusCode(201); // WireMock возвращает 201 Created при успешном создании маппинга
    }

    @Test
    public void test1_StripeSuccess() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/v1/payment_intents")
                .then()
                .statusCode(200)
                .body("id", equalTo("pi_mock_001"))
                .body("status", equalTo("requires_capture"))
                .body("amount", equalTo(15000))
                .body("currency", equalTo("usd"));
    }

    @Test
    public void test2_StripeDeclined() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/v1/payment_intents/declined")
                .then()
                .statusCode(402)
                .body("error.code", equalTo("card_declined"))
                .body("error.decline_code", equalTo("insufficient_funds"))
                .body("error.message", equalTo("Your card has insufficient funds."));
    }

    @Test
    public void test3_StripeServerError() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/v1/payment_intents/error")
                .then()
                .statusCode(503)
                .body("error.type", equalTo("api_error"))
                .body("error.message", equalTo("Service unavailable"));
    }
}
