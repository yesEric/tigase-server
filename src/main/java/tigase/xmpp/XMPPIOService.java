/*  Package Jabber Server
 *  Copyright (C) 2001, 2002, 2003, 2004, 2005
 *  "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.xmpp;

import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import tigase.net.IOService;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;

/**
 * Describe class XMPPIOService here.
 *
 *
 * Created: Tue Feb  7 07:15:02 2006
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class XMPPIOService extends IOService {

  /**
   * Variable <code>log</code> is a class logger.
   */
  private static final Logger log =
		Logger.getLogger("tigase.xmpp.XMPPIOService");

  private XMPPDomBuilderHandler domHandler = null;
	private static SimpleParser parser = SingletonFactory.getParserInstance();
	private XMPPIOServiceListener serviceListener = null;

  /**
   * The <code>waitingPackets</code> queue keeps data which have to be processed.
   */
  private ConcurrentLinkedQueue<Packet> waitingPackets =
    new ConcurrentLinkedQueue<Packet>();

  /**
   * The <code>readyPackets</code> queue keeps data which have been already
   * processed and they are actual processing results.
   */
  private ConcurrentLinkedQueue<Packet> receivedPackets =
    new ConcurrentLinkedQueue<Packet>();

  /**
   * Variable <code>lock</code> keeps reference to object lock.
   * It supports multi-threaded processing and can be called simultanously from
   * many threads. It is not recommended however as lock prevents most of
   * methods to be executed concurrently as they process data received from
   * socket and the data should be processed in proper order.
   */
  private Lock writeLock = new ReentrantLock();
	private Lock readLock = new ReentrantLock();

	private boolean streamClosed = false;

	/**
	 * Creates a new <code>XMPPIOService</code> instance.
	 *
	 */
	public XMPPIOService() {
		domHandler = new XMPPDomBuilderHandler(this);
	}

	public void setIOServiceListener(XMPPIOServiceListener sl) {
		this.serviceListener = sl;
		super.setIOServiceListener(sl);
	}

	protected void xmppStreamOpened(Map<String, String> attribs) {
		writeLock.lock();
		final String response = serviceListener.xmppStreamOpened(this, attribs);
    try {
			log.finest("Sending data: " + response);
			writeData(response);
			assert debug(response, "--SENT:");
			if (response.endsWith("</stream:stream>")) {
				stop();
			} // end of if (response.endsWith())
		} catch (IOException e) {
			log.warning("Error sending stream open data: " + e);
			stop();
    } finally {
			writeLock.unlock();
    }
	}

	public void xmppStreamOpen(final String data) {
		writeLock.lock();
    try {
			log.finest("Sending data: " + data);
			writeData(data);
			assert debug(data, "--SENT:");
		} catch (IOException e) {
			log.warning("Error sending stream open data: " + e);
			stop();
    } finally {
			writeLock.unlock();
    }
	}

	/**
   * Describe <code>stop</code> method here.
   *
   * @exception IOException if an error occurs
   */
  public void stop() {
// 		if (!streamClosed) {
// 			streamClosed = true;
// 			serviceListener.xmppStreamClosed(this);
// 		} // end of if (!streamClosed)
		super.stop();
  }

	protected void xmppStreamClosed() {
		streamClosed = true;
		serviceListener.xmppStreamClosed(this);
		writeLock.lock();
    try {
			log.finest("Sending data: </stream:stream>");
			writeData("</stream:stream>");
		} catch (IOException e) {
			log.warning("Error sending stream closed data: " + e);
    } finally {
			writeLock.unlock();
    }
// 		try {
// 			stop();
// 		} catch (IOException e) {
// 			log.warning("Error stopping service: " + e);
// 		} // end of try-catch
	}

	/**
   * Method <code>addPacketToSend</code> adds new data which will be processed
   * during next run.
   * Data are kept in proper order like in <em>FIFO</em> queue.
   *
   * @param packet a <code>Packet</code> value of data to process.
   */
  public void addPacketToSend(final Packet packet) {
    waitingPackets.offer(packet);
  }

  public Queue<Packet> getReceivedPackets() {
    return receivedPackets;
  }

  /**
   * Describe <code>processWaitingPackets</code> method here.
   *
   */
  public void processWaitingPackets() throws IOException {
    // We change state of this object in this method
    // It can be called by many threads simultanously
    // so we need to make it thread-safe
		writeLock.lock();
    try {
			Packet packet = null;
			while ((packet = waitingPackets.poll()) != null) {
				log.finer("Processing packet: " + packet.getElemName()
					+ ", type: " + packet.getType());
				log.finest("Sending packet: " + packet.getStringData());
				writeData(packet.getStringData());
				assert debug(packet.getStringData(), "--SENT:");
			} // end of while (packet = waitingPackets.poll() != null)
    } finally {
			writeLock.unlock();
    }
  }

	/**
   * Describe <code>processSocketData</code> method here.
   *
   * @exception IOException if an error occurs
   */
  protected void processSocketData() throws IOException {
    // We change state of this object in this method
    // It can be called by many threads simultanously
    // so we need to make it thread-safe
		//log.finer("About to read socket data.");
// 		readLock.lock();
    try {
			if (isConnected()) {
				final char[] data = readData();

				// Yes check again if we are still connected as
				// servce might be disconnected during data read
				if (isConnected() && data != null) {
					log.finest("READ:\n" + new String(data));
					// This is log for debuging only,
					// in normal mode don't even call below code
					assert debug(new String(data), "--RECEIVED:");
					try {
						parser.parse(domHandler, data, 0, data.length);
						Queue<Element> elems = domHandler.getParsedElements();
						if (elems.size() > 0) {
							readCompleted();
						}
						Element elem = null;
						while ((elem = elems.poll()) != null) {
							//	assert debug(elem.toString() + "\n");
							log.finer("Read element: " + elem.getName());
							log.finest("Read packet: " + elem.toString());
							//							System.out.print(elem.toString());
							addReceivedPacket(new Packet(elem));
						} // end of while ((elem = elems.poll()) != null)
					}	catch (Exception ex) {
						ex.printStackTrace();
						stop();
					} // end of try-catch
				} // end of if (isConnected() && data != null)
			}
    } finally {
//       readLock.unlock();
    }
  }

	protected int receivedPackets() {
		return receivedPackets.size();
	}

	/**
   * Method <code>addReceivedPacket</code> puts processing results to queue.
   * The processing results are usually data (messages) which has been
   * just received from socket.
   *
   * @param packet a <code>Packet</code> value of processing results.
   */
  private void addReceivedPacket(final Packet packet) {
    receivedPackets.offer(packet);
  }

} // XMPPIOService
