package com.github.lucasces.mahproxy;

// See http://www.jtmelton.com/2007/11/27/a-simple-multi-threaded-java-http-proxy-server/

import java.io.*;
import java.net.*;
import java.util.*;

import java.nio.charset.StandardCharsets;

import javax.net.*;
import javax.net.ssl.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

public final class ProxyThread extends Thread {
    private static final int BUFFER_SIZE = 32768;
    private static final String threadName = "ProxyThread";

    private final Socket socket;

    static final Logger logger = LoggerFactory.getLogger(
            ProxyThread.class);

    public ProxyThread(Socket socket) {
        super(threadName);
        this.socket = socket;
    }

    private HttpURLConnection createHttpURLConnection(URL url, Map<String, String> headers, Boolean doInput, Boolean doOutput) throws IOException{
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        for(Map.Entry<String, String> e: headers.entrySet()){
            conn.setRequestProperty(e.getKey(), e.getValue());
        }

        conn.setDoInput(doInput);
        conn.setDoOutput(doOutput);

        return conn;
    }

    private Map<String, String> getHeaders(HttpURLConnection huc){
        Map<String, String> headers = new HashMap<String, String>();
        for (int i = 0;; i++) {
            String headerName = huc.getHeaderFieldKey(i);
            String headerValue = huc.getHeaderField(i);

            if (headerName == null && headerValue == null) {
                // No more headers
                break;
            }else if (headerName != null){
                headers.put(headerName, headerValue);

            }
        }

        return headers;
    }

    private String headersToString(Map<String, String> headers){
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, String> e: headers.entrySet()){
            s.append(e.getKey());
            s.append(": ");
            s.append(e.getValue());
            s.append("\n");
        }
        s.append("\n");

        return s.toString();
    }

    private String getStatusLine(HttpURLConnection huc){
        return huc.getHeaderField(0) + "\n";
    }

    private HttpResponse doGet(HttpRequest request) throws IOException{
        HttpResponse response = new HttpResponse();
        HttpURLConnection huc = createHttpURLConnection(request.uri.toURL(), request.headers, true, false);
        response.headers = getHeaders(huc);
        response.in = (huc.getErrorStream() == null ? huc.getInputStream() : huc.getErrorStream());
        response.status = getStatusLine(huc);

        return response;
    }

    private HttpResponse doHead(HttpRequest request) throws IOException{
        HttpResponse response = new HttpResponse();
        HttpURLConnection huc = createHttpURLConnection(request.uri.toURL(), request.headers, true, false);
        huc.setRequestMethod("HEAD");
        response.headers = getHeaders(huc);
        response.in = (huc.getErrorStream() == null ? huc.getInputStream() : huc.getErrorStream());
        response.status = getStatusLine(huc);
        return response;
    }

    private HttpResponse doOptions(HttpRequest request) throws IOException{
        HttpResponse response = new HttpResponse();
        HttpURLConnection huc = createHttpURLConnection(request.uri.toURL(), request.headers, true, false);
        huc.setRequestMethod("OPTIONS");
        response.headers = getHeaders(huc);
        response.in = (huc.getErrorStream() == null ? huc.getInputStream() : huc.getErrorStream());
        response.status = getStatusLine(huc);
        return response;
    }

    private HttpResponse doDelete(HttpRequest request) throws IOException{
        HttpResponse response = new HttpResponse();
        HttpURLConnection huc = createHttpURLConnection(request.uri.toURL(), request.headers, true, false);
        huc.setRequestMethod("DELETE");
        response.headers = getHeaders(huc);
        response.in = (huc.getErrorStream() == null ? huc.getInputStream() : huc.getErrorStream());
        response.status = getStatusLine(huc);
        return response;
    }

    private HttpResponse doPost(HttpRequest request) throws IOException{
        HttpResponse response = new HttpResponse();
        HttpURLConnection huc = createHttpURLConnection(request.uri.toURL(), request.headers, true, true);
        OutputStream out = huc.getOutputStream();

        Stream upstream = new Stream(out, request.in, request.contentLength);
        upstream.run();

        response.headers = getHeaders(huc);
        response.in = (huc.getErrorStream() == null ? huc.getInputStream() : huc.getErrorStream());
        response.status = getStatusLine(huc);
        return response;
    }

    private HttpResponse doPut(HttpRequest request) throws IOException{
        HttpResponse response = new HttpResponse();
        HttpURLConnection huc = createHttpURLConnection(request.uri.toURL(), request.headers, true, true);
        huc.setRequestMethod("PUT");
        OutputStream out = huc.getOutputStream();

        Stream upstream = new Stream(out, request.in, request.contentLength);
        upstream.run();

        response.headers = getHeaders(huc);
        response.in = (huc.getErrorStream() == null ? huc.getInputStream() : huc.getErrorStream());
        response.status = getStatusLine(huc);
        return response;
    }

    private void doConnect(HttpRequest request) throws IOException{
        Socket conn;
        OutputStream upstreamOut;
        InputStream upstreamIn;

        try{
            conn = new Socket(request.uri.getHost(), request.uri.getPort());
        }
        catch(SocketException e){
            byte[] r = "HTTP/1.1 504 Gateway timeout\n\n".getBytes();

            request.out.write(r, 0, r.length);
            request.out.flush();
            return;
        }

        upstreamOut = conn.getOutputStream();
        upstreamIn = conn.getInputStream();

        Stream downStream = new Stream(request.out, upstreamIn);
        Thread downStreamThread = new Thread(downStream, "Dowstream");

        Stream upStream = new Stream(upstreamOut, request.in);
        Thread upStreamThread = new Thread(upStream, "Upstream");

        byte[] r = "HTTP/1.1 200 OK\n\n".getBytes();

        request.out.write(r, 0, r.length);
        request.out.flush();

        downStreamThread.start();
        upStreamThread.start();
    }

    private HttpRequest parseRequest(InputStream in, OutputStream out) throws IOException, URISyntaxException{
            String inputLine;
            Boolean parseHeaders = true;
            int cnt = 0;
            HttpRequest request = new HttpRequest();
            Map<String, String> requestHeaders = new HashMap<String, String>();
            LineIterator lines = IOUtils.lineIterator(in, StandardCharsets.US_ASCII);

            while(lines.hasNext()){
                inputLine = lines.next();

                try {
                    StringTokenizer tok = new StringTokenizer(inputLine);
                    tok.nextToken();
                } catch (Exception e) {
                    break;
                }

                if (cnt == 0) {
                    String[] tokens = inputLine.split(" ");
                    request.method = tokens[0];
                    if (request.method.toUpperCase().equals("CONNECT")){
                        // Add dummy Scheme to use URI's getHost and getPort
                        request.uri = new URI("socket://" + tokens[1]);
                    }else{
                        request.uri = new URI(tokens[1]);
                    }
                }else{
                    if (inputLine.equals("\n")){
                        parseHeaders = false;
                    }
                    if (parseHeaders){
                        String[] tokens = inputLine.split(":");
                        requestHeaders.put(tokens[0].trim(), tokens[1].trim());
                    }
                }

                cnt++;
            }

            request.in = in;
            request.out = out;

            request.headers = requestHeaders;

            if (request.headers.containsKey("Content-Length")){
                request.contentLength = Long.parseLong(request.headers.get("Content-Length"));
            }else{
                request.contentLength = 0;
            }


            request.keepAlive = (request.headers.containsKey("Proxy-Connection") && request.headers.get("Proxy-Connection").equals("keep-alive"));
            request.chunked = (request.headers.containsKey("Transfer-Encoding") && request.headers.get("Transfer-Encoding").equals("chunked"));

            return request;
    }

    private HttpResponse handleRequest(HttpRequest request) throws IOException, URISyntaxException{
        HttpResponse response = null;
        switch(request.method.toUpperCase()){
            case "GET":
                response = doGet(request);
                break;
            case "HEAD":
                response = doHead(request);
                break;
            case "OPTIONS":
                response = doOptions(request);
                break;
            case "DELETE":
                response = doDelete(request);
                break;
            case "POST":
                response = doPost(request);
                break;
            case "PUT":
                response = doPut(request);
                break;
            case "CONNECT":
                doConnect(request);
                return null;
        }

        return response;
    }

    private void dispatchResponse(HttpRequest request,HttpResponse response) throws IOException{
        // Headers
        String headers = headersToString(response.headers);
        request.out.write(response.status.getBytes(), 0, response.status.length());
        request.out.write(headers.getBytes(), 0, headers.length());
        request.out.flush();

        // Body
        long writen = 0;
        long contentLength = 0;
        if (response.headers.containsKey("Content-Length")){
            contentLength = Long.parseLong(response.headers.get("Content-Length"));
        }
        byte by[] = new byte[BUFFER_SIZE];

        int index = response.in.read(by, 0, BUFFER_SIZE);
        while (writen < contentLength){
            request.out.write(by, 0, index);
            writen += index;

            if (writen < contentLength){
                index = response.in.read(by, 0, BUFFER_SIZE);
            }
        }
        request.out.flush();
        response.in.close();
        logger.info(String.format("%1$s, %2$s, %3$s, %4$d", request.method, request.uri.toString(), response.status, contentLength).toString());
    }

    private void closeConn(HttpRequest request, Socket socket){
        try{
            if (request != null && !request.method.equals("CONNECT")){
                if (request.out != null) {
                    request.out.close();
                }
                if (request != null && request.in != null) {
                    request.in.close();
                }
                if (socket != null) {
                    socket.close();
                }

            }
        }catch(Exception e){
            logger.error(ExceptionUtils.getStackTrace(e));
        }
    }

    public void run() {
        HttpResponse response = null;
        HttpRequest request = null;

        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            while(socket.isConnected()){
                request = parseRequest(in, out);
                //---------------------------------------------
                // Suitable places for request filtering/caching
                //---------------------------------------------
                response = handleRequest(request);

                if (response != null){
                    dispatchResponse(request, response);
                }

                break;
            }

        } catch(SocketException e){
            return;
        }
        catch (Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
            return;
        }
        finally{
            closeConn(request, socket);
        }
    }
}

class HttpResponse{
    public InputStream in;
    public Map<String, String> headers;
    public String status;
}

class HttpRequest{
    public InputStream in;
    public OutputStream out;
    public String method;
    public long contentLength;
    public URI uri;
    public Map<String, String> headers;
    Boolean keepAlive;
    Boolean chunked;
}

class Stream implements Runnable{
    private static final int BUFFER_SIZE = 32768;

    OutputStream out;
    InputStream in;
    long length = -1;

    public Stream(OutputStream out, InputStream in){
        this.out = out;
        this.in = in;
    }

    public Stream(OutputStream out, InputStream in, long length){
        this.out = out;
        this.in = in;
        length = length;
    }

    public void run(){
        try{
            if (length == -1){
                IOUtils.copyLarge(in, out);
            }else{
                IOUtils.copyLarge(in, out, 0, length);
            }
        }catch(IOException e){
        }
    }
}
