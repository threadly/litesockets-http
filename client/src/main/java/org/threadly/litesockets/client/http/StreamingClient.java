package org.threadly.litesockets.client.http;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;

import org.threadly.concurrent.future.ListenableFuture;


/**
 * Basic interface for StreamingClients.
 * 
 * @author lwahlmeier
 *
 */
public interface StreamingClient {
  /**
   * This is called to enable ssl on this connection.  This can only be done before connect is called.
   * This is the default ssl handler meaning there is no server side certificate validation done.
   * 
   */
  public void enableSSL();
  
  /**
   * This is called to enable ssl on this connection.  This can only be done before connect is called.
   * This method allows you to set the {@link SSLEngine} to use for this connection allow you to validate the certificates if needed.
   * 
   * @param ssle the {@link SSLEngine} to use for this connection.
   */
  public void enableSSL(SSLEngine ssle);
  
  /**
   * This sets the timeout on the connection to the remote host.  This can only be set before the {@link #connect} is called. 
   * 
   * @param timeout the timeout in milliseconds to use for this connection.
   */
  public void setConnectionTimeout(int timeout);
  
  /**
   * This performs a write to the connection.
   * 
   * @param bb the {@link ByteBuffer} to write to socket.
   * @return a {@link ListenableFuture} that will be completed once the frame has been fully written to the socket.
   */
  public ListenableFuture<?> write(ByteBuffer bb);
  
  /**
   * This is called to connect this client to the server.  
   * This will also send in the HTTP upgrade request once the TCP connection is finished.
   * 
   * @return a {@link ListenableFuture} that will be completed when the upgrade is complete, if the upgrade fails the a {@link ListenableFuture} be failed.
   */
  public ListenableFuture<Boolean> connect();
  
  /**
   * Sets a runnable to be ran once this connection is closed.
   * 
   * @param cl the runnable to run on the connection close.
   */
  public void addCloseListener(Runnable cl);
  
  /**
   * 
   * @return true if the client is connected and false if not.
   */
  public boolean isConnected();
  
  /**
   * This will close the client.  
   */
  public void close();
  
  /**
   * Gets the {@link Executor} for this client.
   * 
   * @return the {@link Executor} for this client.
   */
  public Executor getClientsThreadExecutor();
}
