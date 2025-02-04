package com.elepy.tests.http;


import com.elepy.exceptions.ElepyErrorMessage;
import com.elepy.exceptions.ElepyException;
import com.elepy.http.HttpService;
import com.elepy.uploads.FileUpload;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public abstract class HttpServiceTest {


    private HttpService service;
    private HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    @BeforeEach
    void setUp() {
        service = httpService();
        service.port(3030);
    }

    public abstract HttpService httpService();


    @AfterEach
    void tearDown() {
        service.stop();
    }

    @Test
    void can_StartService_without_Exception() {
        service.get("/", context -> context.result("Hello World"));
        assertDoesNotThrow(service::ignite);
    }

    @Test
    void can_handleGET() {
        service.get("/test", (request, response) -> response.result("hi"));


        service.ignite();
        assertResponseReturns("get", "/test", "hi");


    }

    @Test
    void can_handleStaticFiles() throws IOException, InterruptedException {
        service.staticFiles("static");

        service.get("/test", ctx -> ctx.result("hi"));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://localhost:3030/doggo.jpg")))
                .build();

        final HttpResponse<InputStream> send = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());


        //assert it doesn't interfere with existing request.
        assertResponseReturns("get", "/test", "hi");
        assertTrue(IOUtils.contentEquals(send.body(), inputStream("static/doggo.jpg")),
                "Static file returning a wrong input stream");


    }

    @Test
    void can_handlePOST() {
        service.post("/test", (request, response) -> response.result("hi"));

        service.ignite();
        assertResponseReturns("post", "/test", "hi");
    }

    @Test
    void can_handlePUT() {
        service.put("/test", ctx -> ctx.result("hi"));

        service.ignite();
        assertResponseReturns("put", "/test", "hi");
    }

    @Test
    void can_handlePATCH() {
        service.patch("/test", ctx -> ctx.result("hi"));

        service.ignite();
        assertResponseReturns("patch", "/test", "hi");
    }


    @Test
    void can_handleDELETE() {
        service.delete("/test", ctx -> ctx.result("hi"));

        service.ignite();
        assertResponseReturns("delete", "/test", "hi");
    }

    @Test
    void can_handleMultipleRoutes() {
        service.get("/testGET", (request, response) -> response.result("hiGET"));
        service.post("/testPOST", (request, response) -> response.result("hiPOST"));
        service.put("/testPUT", ctx -> ctx.result("hiPUT"));
        service.patch("/testPATCH", ctx -> ctx.result("hiPATCH"));
        service.delete("/testDELETE", ctx -> ctx.result("hiDELETE"));

        service.ignite();

        assertResponseReturns("delete", "/testDELETE", "hiDELETE");
        assertResponseReturns("patch", "/testPATCH", "hiPATCH");
        assertResponseReturns("put", "/testPUT", "hiPUT");
        assertResponseReturns("post", "/testPOST", "hiPOST");
        assertResponseReturns("get", "/testGET", "hiGET");

    }

    @Test
    void can_handleException_inRoute() throws IOException, InterruptedException {
        service.get("/testException", (request, response) -> {
            response.result("Exception not handled");
            throw new ElepyException("Exception handled", 400);
        });

        service.exception(ElepyException.class, (e, context) -> {
            context.result(e.getMessage());
            context.status(e.getStatus());
        });
        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/testException")).GET().build();

        final HttpResponse<String> send = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals("Exception handled", send.body());
        assertEquals(400, send.statusCode());
    }

    @Test
    void can_handleException_inBefore() throws IOException, InterruptedException {

        service.before(context -> {
            throw new ElepyException("Exception handled", 400);
        });

        service.get("/testException2", (request, response) -> {
            response.result("Exception not handled");
        });

        service.exception(ElepyErrorMessage.class, (e, context) -> {
            context.result(e.getMessage());
            context.status(e.getStatus());
        });
        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/testException2")).GET().build();

        final HttpResponse<String> send = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals("Exception handled", send.body());
        assertEquals(400, send.statusCode());
    }

    @Test
    void can_handleException_inAfter() throws IOException, InterruptedException {

        service.after(context -> {
            throw new ElepyException("Exception handled", 400);
        });

        service.get("/testException2", (request, response) -> {
            response.result("Exception not handled");
        });

        service.exception(ElepyErrorMessage.class, (e, context) -> {
            context.result(e.getMessage());
            context.status(e.getStatus());
        });
        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/testException2")).GET().build();

        final HttpResponse<String> send = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals("Exception handled", send.body());
        assertEquals(400, send.statusCode());
    }


    @Test
    void requests_haveProper_QueryString() throws IOException, InterruptedException {

        AtomicReference<String> queryString = new AtomicReference<>();
        service.get("/queryString", (request, response) ->
                queryString.set(request.queryString()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString?q=a&a=b")).GET().build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo("q=a&a=b"));

    }

    @Test
    void requests_haveProper_Method() throws IOException, InterruptedException {

        AtomicReference<String> queryString = new AtomicReference<>();
        service.get("/queryString", (request, response) ->
                queryString.set(request.method()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString?q=a&a=b")).GET().build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo("GET"));

    }

    @Test
    void requests_haveProper_Scheme() throws IOException, InterruptedException {

        AtomicReference<String> queryString = new AtomicReference<>();
        service.get("/queryString", (request, response) ->
                queryString.set(request.scheme()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString?q=a&a=b")).GET().build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo("http"));

    }

    @Test
    void requests_haveProper_Host() throws IOException, InterruptedException {

        AtomicReference<String> queryString = new AtomicReference<>();
        service.get("/queryString", (request, response) ->
                queryString.set(request.host()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString?q=a&a=b")).GET().build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo("localhost:3030"));

    }


    @Test
    void requests_haveProper_Body() throws IOException, InterruptedException {

        AtomicReference<String> queryString = new AtomicReference<>();
        service.post("/queryString", ctx ->
                queryString.set(ctx.body()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString"))
                .POST(HttpRequest.BodyPublishers.ofString("theBody"))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo("theBody"));

    }


    @Test
    void requests_haveProper_BodyAsBytes() throws IOException, InterruptedException {
        final byte[] theBytes = new byte[]{
                0B1010,
                0B1110,
                0B1011,
                0B1110,
                0B1111,
        };

        AtomicReference<byte[]> queryString = new AtomicReference<>();
        service.post("/queryString", ctx ->
                queryString.set(ctx.bodyAsBytes()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString"))
                .POST(HttpRequest.BodyPublishers.ofByteArray(theBytes))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo(theBytes));

    }


    @Test
    void requests_haveProper_IP() throws IOException, InterruptedException {

        final String hostAddress = InetAddress.getByName("localhost").getHostAddress();

        AtomicReference<String> queryString = new AtomicReference<>();


        service.post("/queryString", ctx ->
                queryString.set(ctx.ip()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString"))
                .POST(HttpRequest.BodyPublishers.ofString("theBody"))
                .build();

        final HttpResponse<String> send = httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo(hostAddress));

    }

    @Test
    void requests_haveProper_URL() throws IOException, InterruptedException {

        AtomicReference<String> queryString = new AtomicReference<>();


        service.post("/queryString", ctx ->
                queryString.set(ctx.url()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString"))
                .POST(HttpRequest.BodyPublishers.ofString("theBody"))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo("http://localhost:3030/queryString"));

    }

    @Test
    void requests_haveProper_URI() throws IOException, InterruptedException {

        AtomicReference<String> queryString = new AtomicReference<>();


        service.post("/queryString", ctx ->
                queryString.set(ctx.uri()));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString"))
                .POST(HttpRequest.BodyPublishers.ofString("theBody"))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo("/queryString"));

    }

    @Test
    void requests_haveProper_PathParams() throws IOException, InterruptedException {

        AtomicReference<String> queryString = new AtomicReference<>();

        AtomicReference<Map<String, String>> pathParams = new AtomicReference<>();


        service.post("/:pathparam1/x/:pathparam2", ctx -> {

            pathParams.set(ctx.params());
            queryString.set(ctx.params("pathparam2"));
        });

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString/x/thePathParam"))
                .POST(HttpRequest.BodyPublishers.ofString("theBody"))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryString, equalTo("thePathParam"));
        await().atMost(1, TimeUnit.SECONDS).untilAtomic(pathParams, allOf(hasEntry("pathparam1", "queryString"), hasEntry("pathparam2", "thePathParam")));

    }

    @Test
    void requests_haveProper_QueryParams() throws IOException, InterruptedException {

        AtomicReference<String> queryParamValueQ = new AtomicReference<>();
        AtomicReference<Set<String>> queryParams = new AtomicReference<>();
        AtomicReference<String[]> queryParamValuesX = new AtomicReference<>();

        service.get("/queryString", (request, response) -> {

            queryParams.set(request.queryParams());
            queryParamValueQ.set(request.queryParamOrDefault("q", "NOT_FOUND"));
            queryParamValuesX.set(request.queryParamValues("x"));
        });

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString?q=a&x=y&x=z")).GET().build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryParamValueQ, equalTo("a"));
        await().atMost(1, TimeUnit.SECONDS).untilAtomic(queryParams,
                allOf(hasItems("q", "x"), not(hasItem("NONO"))));


        await().atMost(1, TimeUnit.SECONDS)
                .untilAtomic(queryParamValuesX,
                        allOf(
                                hasItemInArray("y"),
                                hasItemInArray("z"),
                                not(hasItemInArray("a"))
                        ));

    }

    @Test
    void requests_haveProper_Headers() throws IOException, InterruptedException {

        AtomicReference<String> headerQ = new AtomicReference<>();
        AtomicReference<Set<String>> headers = new AtomicReference<>();

        service.get("/queryString", (request, response) -> {
            headers.set(request.headers());
            headerQ.set(request.headers("q"));
        });

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString?q=a&x=y&x=z")).GET()
                .headers("x", "y", "q", "a").build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(headerQ, equalTo("a"));
        await().atMost(1, TimeUnit.SECONDS).untilAtomic(headers,
                allOf(hasItems("q", "x"), not(hasItem("NONO"))));

    }

    @Test
    void requests_haveProper_Attributes() throws IOException, InterruptedException {

        AtomicReference<String> attribute = new AtomicReference<>();

        service.before("/queryString", (request, response) -> {

            request.attribute("attribute", "theAttribute");
        });

        service.get("/queryString", (request, response) -> attribute.set(request.attribute("attribute")));

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString?q=a&x=y&x=z")).GET()
                .headers("x", "y", "q", "a").build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        await().atMost(1, TimeUnit.SECONDS).untilAtomic(attribute, equalTo("theAttribute"));

    }


    @Test
    void requests_haveProper_FileUploads() throws IOException, UnirestException {

        AtomicReference<List<FileUpload>> atomicReference = new AtomicReference<>();

        service.post("/uploads", (request, response) -> atomicReference.set(request.uploadedFiles("files")));

        service.ignite();
        final InputStream file1 = inputStream("cv.pdf");

        Unirest.post("http://localhost:3030/uploads")
                .field("files", file1, "cv.pdf")
                .asString();

        file1.close();

        await().atMost(5, TimeUnit.SECONDS).untilAtomic(atomicReference, hasSize(1));

        final List<FileUpload> fileUploads = atomicReference.get();
        final FileUpload fileUpload1 = fileUploads.get(0);

        assertTrue(IOUtils.contentEquals(fileUpload1.getContent(), inputStream("cv.pdf")));
    }

    @Test
    void responses_haveProper_Type() throws IOException, InterruptedException {

        service.post("/queryString", ctx -> {
            ctx.response().type("application/xml");
        });

        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/queryString"))
                .POST(HttpRequest.BodyPublishers.ofString("theBody"))
                .build();

        final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        assertTrue(response.headers().firstValue("Content-Type").orElseThrow()
                .equalsIgnoreCase("application/xml"));

    }

    @Test
    void responses_RedirectProperly() throws IOException, InterruptedException {

        service.get("/redir", context -> {
            context.redirect("http://google.com");
        });


        service.ignite();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3030/redir"))
                .GET()
                .build();

        final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());


        assertEquals(URI.create("http://www.google.com/"), response.uri());

    }

    private void assertResponseReturns(String method, String path, Object expectedResult) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://localhost:3030%s", path)))
                .method(method.toUpperCase(), HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            final HttpResponse<String> send = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(expectedResult, send.body());
            assertEquals(200, send.statusCode());
        } catch (IOException e) {
            throw new AssertionError("network error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("thread error", e);
        }
    }

    private InputStream inputStream(String name) {
        return Optional.ofNullable(this.getClass().getResourceAsStream("/" + name))
                .orElseThrow(() -> new AssertionFailedError(String.format("The file '%s' can't be found in resources", name)));
    }
}
