package com.hyperwallet.clientsdk.util;

import cc.protea.util.http.Request;
import cc.protea.util.http.Response;
import com.hyperwallet.clientsdk.HyperwalletException;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MultipartRequest extends Request{
    final String BOUNDARY = "--0011010110123111";
    final String CRLF = "\r\n";
    final String SEPARATOR = "--";
    final String DATA = "data";

    HttpURLConnection connection;
    Multipart multipartList;
    DataOutputStream outStream;
    Map<String, List<String>> headers = new HashMap<String, List<String>>();
    String requestURL;

    private final String username;
    private final String password;

    public Multipart getMultipartList() {
        return multipartList;
    }

    public void setMultipartList(Multipart multipartList) {
        this.multipartList = multipartList;
    }

    MultipartRequest(String url, Multipart multipartList, String username, String password) throws IOException {
        super(url);
        requestURL = url;
        this.username  = username;
        this.password = password;
        this.multipartList = multipartList;
    }

    public Response putResource() throws IOException {
        Response response = new Response() ;
        buildHeaders();
        URL url = new URL(requestURL);
        final String pair = username + ":" + password;
        final String base64 = DatatypeConverter.printBase64Binary(pair.getBytes());
        connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true); // indicates POST method
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("authorization", "Basic " + base64);
        connection.setRequestProperty("accept", "application/json");
        connection.setRequestProperty(
                "Content-Type", "multipart/form-data; boundary="+BOUNDARY);
        outStream = new DataOutputStream(this.connection.getOutputStream());
        writeMultipartBody();
        outStream.flush();
        outStream.close();
        // checks server's status code first
        int status = this.connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_CREATED) {
            InputStream responseStream = new
                BufferedInputStream(connection.getInputStream());
            BufferedReader responseStreamReader =
                new BufferedReader(new InputStreamReader(responseStream));
            String line = "";
            StringBuilder stringBuilder = new StringBuilder();
            while ((line = responseStreamReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            responseStreamReader.close();
            response.setResponseCode(status);
            response.setBody(stringBuilder.toString());
            response.setHeaders(this.connection.getHeaderFields());
            this.connection.disconnect();
        } else {
            throw new HyperwalletException("Server returned non-OK status: " + status);
        }
        return response;
    }

    private void buildHeaders() {
        if (!headers.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                List<String> values = entry.getValue();

                for (String value : values) {
                    connection.addRequestProperty(entry.getKey(), value);
                }
            }
        }
    }

    private void writeMultipartBody() throws IOException {
        for(Multipart.MultipartData multipartData : multipartList.getMultipartList()) {
            for (Map.Entry<String, String> entry : multipartData.getEntity().entrySet()) {

                outStream.writeBytes(this.SEPARATOR + this.BOUNDARY + this.CRLF);
                outStream.writeBytes(multipartData.getContentDisposition());
                outStream.writeBytes(multipartData.getContentType());
                outStream.writeBytes(this.CRLF);

                if(multipartData.getContentType().contains("image")){
                    byte[] bytes = Files.readAllBytes(new File(entry.getValue().toString()).toPath());
                    outStream.write(bytes);
                    outStream.writeBytes(this.CRLF);
                }else{
                    outStream.writeBytes(entry.getValue() + this.CRLF);
                }
                outStream.flush();
            }
        }
        outStream.writeBytes(this.CRLF);
        outStream.writeBytes(this.SEPARATOR + this.BOUNDARY + this.SEPARATOR + this.CRLF);

    }
}

