/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2007 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.xmpp.impl;

import java.util.Arrays;
import java.util.Queue;
import java.util.Map;
import java.util.logging.Logger;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPProcessor;
import tigase.xmpp.XMPPProcessorIfc;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.XMPPException;
import tigase.db.NonAuthUserRepository;

/**
 * Describe class SessionBind here.
 *
 *
 * Created: Mon Feb 20 22:43:59 2006
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class SessionBind extends XMPPProcessor
	implements XMPPProcessorIfc {

  private static final String SESSION_KEY = "Session-Set";

  private static final String XMLNS = "urn:ietf:params:xml:ns:xmpp-session";
  private static final Logger log =
		Logger.getLogger("tigase.xmpp.impl.SessionBind");

	private static final String ID = XMLNS;
  private static final String[] ELEMENTS = {"session"};
  private static final String[] XMLNSS = {XMLNS};
  private static final Element[] FEATURES = {
		new Element("session", new String[] {"xmlns"}, new String[] {XMLNS})};
  private static final Element[] DISCO_FEATURES =	{
		new Element("feature", new String[] {"var"}, new String[] {XMLNS})
	};

  public Element[] supDiscoFeatures(final XMPPResourceConnection session)
	{ return Arrays.copyOf(DISCO_FEATURES, DISCO_FEATURES.length); }


  private static int resGenerator = 0;

	public String id() { return ID; }

	public String[] supElements()
	{ return Arrays.copyOf(ELEMENTS, ELEMENTS.length); }

	public String[] supNamespaces()
	{ return Arrays.copyOf(XMLNSS, XMLNSS.length); }

  public Element[] supStreamFeatures(final XMPPResourceConnection session)	{
    if (session.getSessionData(SESSION_KEY) != null) {
      return null;
    } else {
      return Arrays.copyOf(FEATURES, FEATURES.length);
    } // end of if (session.isAuthorized()) else
	}

  public void process(final Packet packet, final XMPPResourceConnection session,
		final NonAuthUserRepository repo, final Queue<Packet> results,
		final Map<String, Object> settings)
		throws XMPPException {

		if (session == null) {
			return;
		} // end of if (session == null)

		if (!session.isAuthorized()) {
      results.offer(session.getAuthState().getResponseMessage(packet,
          "Session is not yet authorized.", false));
			return;
		} // end of if (!session.isAuthorized())

		//Element request = packet.getElement();
    StanzaType type = packet.getType();
		switch (type) {
		case set:
			session.putSessionData(SESSION_KEY, "true");
			results.offer(packet.okResult((String)null, 0));
			break;
		default:
			results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet,
					"Session type is incorrect", false));
			break;
		} // end of switch (type)
	}

} // SessionBind
