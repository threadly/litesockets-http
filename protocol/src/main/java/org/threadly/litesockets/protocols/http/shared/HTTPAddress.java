package org.threadly.protocols.http.shared;

/**
 * Simple Immutable class that is used for http Connections.  Its mainly used
 * to know if 2 address will connect to the exact same place or not. 
 *
 */
public class HTTPAddress {
  private final String host;
  private final int port;
  private final boolean doSSL;
  private final String finalString;
  
  public HTTPAddress(String host, int port, boolean doSSL) {
    this.host = host.intern();
    this.port = port;
    this.doSSL = doSSL;
    finalString = ("HTTPAddress:"+host+":"+port+":SSL:"+doSSL).intern(); 
  }
  
  public String getHost() {
    return host;
  }
  
  public int getPort() {
    return port;
  }
  
  public boolean getdoSSL() {
    return doSSL;
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
