package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;


/**
 * Copyright (c) Tuenti Technologies. All rights reserved.
 *
 * @author Manuel Peinado Gallego <mpeinado@tuenti.com>
 */
public class StreamsPacketExtension extends AbstractPacketExtension {
	public static final String ELEMENT_NAME = "streams";

	private List<StreamPacketExtension> streamList = new ArrayList<StreamPacketExtension>();

	/**
	 * Creates a new instance of this <tt>EncryptionPacketExtension</tt>.
	 */
	public StreamsPacketExtension() {
		super(null, ELEMENT_NAME);
	}

	public void addStream(StreamPacketExtension stream) {
		if(!streamList.contains(stream)) {
			streamList.add(stream);
		}
	}

	/**
	 * Returns a <b>reference</b> to the list of <tt>crypto</tt> elements that
	 * we have registered with this encryption element so far.
	 *
	 * @return  a <b>reference</b> to the list of <tt>crypto</tt> elements that
	 * we have registered with this encryption element so far.
	 */
	public List<StreamPacketExtension> getStreamList() {
		return streamList;
	}
}
