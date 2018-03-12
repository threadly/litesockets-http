package org.threadly.litesockets.protocols.http.shared;

import java.net.URL;

/**
 * Simple Immutable class that is used for http Connections.  
 * This is basically just the protocol://host:port of a {@link URL} object
 * and is mostly used to compare if connections are to the same place.
 *
 */
public class HTTPAddress {
  private final String host;
  private final int port;
  private final boolean doSSL;
  private final String finalString;
  private final URL url;

  /**
   * Constructs an HTTPAddress from a URL.  Only the protocol host and port will be used
   * all other arguments in the url will be striped.
   * 
   * @param url the url to make the HTTPAddress from.
   */
  public HTTPAddress(URL url) {
    int port = url.getDefaultPort();
    if(url.getPort() > 0) {
      port = url.getPort();
    }
    this.port = port;
    this.host = url.getHost();
    this.doSSL = url.getProtocol().equalsIgnoreCase("https");
    finalString = ("HTTPAddress:"+host+":"+port+":SSL:"+doSSL);
    try {
      if(doSSL) {
        this.url = new URL("https://"+host+":"+port);
      } else {
        this.url = new URL("http://"+host+":"+port);
      }
    } catch(Exception e) {
      throw new IllegalStateException("Bad URL:"+url);
    }
  }

  /**
   * Construct an HTTPAdrress by specifying the host port and protocol(https or http).
   * 
   * @param host the host to connect too, this can be an ip address or hostname.
   * @param port the port to connect too.
   * @param doSSL If ssl should be used or not on this connection.
   */
  public HTTPAddress(String host, int port, boolean doSSL) {
    if(port <=0 || port > 65535) {
      throw new IllegalArgumentException("The port must be > 0 and < 65535, "+port+" was given.");
    }
    this.host = host;
    this.port = port;
    this.doSSL = doSSL;
    
    finalString = ("HTTPAddress:"+host+":"+port+":SSL:"+doSSL);
    try {
      if(doSSL) {
        this.url = new URL("https://"+host+":"+port);
      } else {
        this.url = new URL("http://"+host+":"+port);
      }
    } catch(Exception e) {
      throw new IllegalStateException("Bad URL:"+host+":"+port+":"+doSSL);
    }
  }

  /**
   * Returns the host portion of this HTTPAddress.
   * 
   * @return the host for this HTTPAddress.
   */
  public String getHost() {
    return host;
  }

  /**
   * Returns the port portion of this HTTPAddress.
   * 
   * @return the port for this HTTPAddress.
   */
  public int getPort() {
    return port;
  }

  /**
   * Should ssl be done on this HTTPAddress.
   * 
   * @return true if ssl should be done and false if not.
   */
  public boolean getdoSSL() {
    return doSSL;
  }
  
  /**
   * Gets the {@link URL} of this HTTPAddress.  Note this will not have a path, query or user in it. 
   * 
   * @return the url for this HTTPAddress.
   */
  public URL getURL() {
    return url;
  }

  @Override
  public String toString() {
    return finalString;
  }

  @Override
  public int hashCode() {
    return finalString.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if(o.hashCode() == this.hashCode()) {
      if(o instanceof HTTPAddress) {
        HTTPAddress h = (HTTPAddress) o;
        if(h.finalString.equals(this.finalString)) {
          return true;
        }
      }
    }
    return false;
  }
}
