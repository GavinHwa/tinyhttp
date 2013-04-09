/* Copyright (C) 2013 Leonardo Bispo de Oliveira
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package br.com.is.http.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLContext;

import br.com.is.nio.EventLoop;

final class HTTPChannel {
  private final SocketChannel channel;
  private final SSLContext    sslContext;
  private final EventLoop     manager;
  private ByteBuffer          remainingData;
  
  HTTPChannel(final SocketChannel channel, final SSLContext sslContext, final EventLoop manager) {
    this.channel    = channel;
    this.sslContext = sslContext;
    this.manager    = manager;
  }
  
  SocketChannel getSocketChannel() {
    return channel;
  }

  boolean handshake() {
    return true;
  }

  long write(final ByteBuffer buffer) throws IOException {
    if (buffer == null)
      return -1;
    
    //TODO: MUST CHECK IF IT IS SSL
    return channel.write(buffer);
  }
  
  private long moveRemaining(final ByteBuffer buffer, int maxLength) {
    if (maxLength == -1) maxLength = buffer.remaining();
    int maxTransfer = Math.min(remainingData.remaining(), maxLength);
    if (maxTransfer > 0) {
      buffer.put(remainingData.array(), 0, maxTransfer);
      remainingData.position(maxTransfer);
      remainingData.compact();
      remainingData.flip();
      if (!remainingData.hasRemaining())
        remainingData = null;
    }
    
    return maxTransfer;
  }
  long read(final ByteBuffer buffer) throws IOException {
    if (buffer == null)
      return 0;
    
    if (remainingData != null) {
      return moveRemaining(buffer, -1);
    }
    
    //TODO: MUST CHECK IF IT IS SSL
    return channel.read(buffer);
  }
  
  long read(final ByteBuffer buffer, int maxLength) throws IOException {
    if (buffer == null)
      return 0;
    
    if (remainingData != null)
      return moveRemaining(buffer, maxLength);
    
    int len = channel.read(buffer);
    
    if (len > maxLength) {
      remainingData = ByteBuffer.allocate(buffer.limit());
      remainingData.put(buffer.array(), maxLength - 1, buffer.limit() - maxLength);
      buffer.limit(maxLength + 1);

      remainingData.position(maxLength);
      remainingData.compact();
      remainingData.flip();
    }
    
    //TODO: MUST CHECK IF IT IS SSL
    return (len < maxLength) ? len : maxLength;
  }
  
  boolean shutdown() throws IOException {
    return true;
  }

  void close() throws IOException {
    manager.unregisterWriterListener(channel);
    manager.unregisterReaderListener(channel);
    channel.close();
  }
  
  SSLContext getSSLContext() {
    return sslContext;
  }
  
  void setRemaining(final ByteBuffer buffer) {
    if (buffer.hasRemaining())
      remainingData = buffer;
  }
}
