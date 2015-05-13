package org.threadly.litesockets.protocol.http;

import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import org.junit.Test;
import org.threadly.litesockets.protocol.http.HTTPConstants.REQUEST_TYPE;
import org.threadly.litesockets.protocol.http.structures.HTTPRequest;
import org.threadly.litesockets.protocol.http.structures.HTTPRequest.HTTPRequestBuilder;

public class HTTPRequestBuilderTests {

  @Test
  public void builder1Test() throws MalformedURLException {
    HTTPRequestBuilder builder = new HTTPRequestBuilder();
    HTTPRequest hr = builder.build();
    assertEquals("GET / HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    assertEquals("*/*", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_ACCEPT));
    assertEquals("litesockets", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_USER_AGENT));
    hr = builder.setPath("/home/somepath").build();
    assertEquals("GET /home/somepath HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    hr = builder.addQuery("test", "test").build();
    assertEquals("GET /home/somepath?test=test HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    
    hr = builder.removeQuery("test").build();
    assertEquals("GET /home/somepath HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    hr = builder.addQuery("blah1112", "").build();
    assertEquals("GET /home/somepath?blah1112 HTTP/1.1", hr.getHTTPRequestHeaders().toString());

    hr = builder.addHeader("X-Something", "Value").build();
    assertEquals("Value", hr.getHTTPHeaders().getHeader("X-Something"));
    
    hr = builder.removeHeader("X-Something").build();
    assertEquals(null, hr.getHTTPHeaders().getHeader("X-Something"));
    
    hr = builder.addBody("X-Something").build();
    assertEquals(11, hr.getBody().remaining());
    byte[] ba = new byte[hr.getBody().remaining()];
    hr.getBody().get(ba);
    assertTrue(Arrays.equals("X-Something".getBytes(), ba));
    
    hr = builder.setHost("blah").build();
    assertEquals("blah", hr.getHost());
    assertEquals("blah", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_HOST));
    
    assertEquals(HTTPConstants.DEFAULT_READ_TIMEOUT, hr.getTimeout());
    hr = builder.setReadTimeout(1).build();
    assertEquals(1, hr.getTimeout());
    
    assertFalse(hr.doSSL());
    hr = builder.enableSSL().build();
    assertTrue(hr.doSSL());
    hr = builder.disableSSL().build();
    assertFalse(hr.doSSL());
    assertFalse(builder.isChunkedRequest());
    hr = builder.enableChunked().build();
    assertTrue(builder.isChunkedRequest());
    assertEquals("chunked", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING));
    assertTrue(hr.isChunked());
    
    assertEquals(16, hr.getBody().remaining());
    ba = new byte[hr.getBody().remaining()];
    hr.getBody().get(ba);
    assertEquals("X-Something", new String(ba, 3, 11));
    
    hr = builder.setURL(new URL("https://google.com/test/?path2=1")).build();
    assertEquals("GET /test/?path2=1 HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    assertEquals("google.com", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_HOST));
    assertEquals("chunked", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING));
    
    hr = builder.setURL(new URL("https://google.com:99999")).build();
    System.out.println(hr);
    assertEquals("GET / HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    assertEquals("google.com", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_HOST));
    assertEquals("chunked", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING));
    assertEquals(99999, hr.getPort());
    assertEquals(true, hr.doSSL());
    
    
    hr = new HTTPRequestBuilder(new URL("https://google.com:99999")).build();
    System.out.println(hr);
    assertEquals("GET / HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    assertEquals("google.com", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_HOST));
    assertEquals(99999, hr.getPort());
    assertEquals(true, hr.doSSL());
    
    hr = new HTTPRequestBuilder(new URL("http://google.com:99999")).build();
    assertFalse(hr.doSSL());
    System.out.println(hr);
    assertEquals("GET / HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    assertEquals("google.com", hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_HOST));
    assertEquals(99999, hr.getPort());
    
    
    hr = builder.disableChunked().build();
    assertEquals(null, hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING));
    assertEquals(0, hr.getBody().remaining());

    
    hr = builder.setPort(200).build();
    assertEquals(200, hr.getPort());
    assertEquals(null, hr.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING));
    assertEquals(0, hr.getBody().remaining());

    
    
    hr = builder.addQueryString("?test=2&i=p").removeQuery("i").build();
    assertEquals("GET /?test=2 HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    
    hr = builder.appendBody("X-Something".getBytes()).appendBody("X-Something".getBytes()).build();
    assertEquals(22, hr.getBody().remaining());
    ba = new byte[hr.getBody().remaining()];
    hr.getBody().get(ba);
    assertTrue(Arrays.equals("X-SomethingX-Something".getBytes(), ba));
    
    
    hr = builder.setRequestType(REQUEST_TYPE.POST).removeQuery("i").build();
    assertEquals("POST /?test=2 HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    
    hr = builder.setRequestType(REQUEST_TYPE.HEAD).removeQuery("i").build();
    assertEquals("HEAD /?test=2 HTTP/1.1", hr.getHTTPRequestHeaders().toString());
    
    hr = builder.buildHeadersOnly();
    assertEquals(0,  hr.getBody().remaining());
    
  }
  
  @Test(expected=IllegalArgumentException.class)
  public void badport() throws MalformedURLException {
    HTTPRequestBuilder builder = new HTTPRequestBuilder();
    HTTPRequest hr = builder.setPort(-40).build();
  }
  
  @Test(expected=IllegalArgumentException.class)
  public void badport2() throws MalformedURLException {
    HTTPRequestBuilder builder = new HTTPRequestBuilder();
    HTTPRequest hr = builder.setPort(457874).build();
  }
  
  @Test(expected=IllegalArgumentException.class)
  public void badURL() throws MalformedURLException {
    HTTPRequestBuilder builder = new HTTPRequestBuilder(new URL("file:///test.com:555"));
  }
}
