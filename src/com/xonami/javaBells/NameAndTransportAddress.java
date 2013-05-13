package com.xonami.javaBells;

import org.ice4j.TransportAddress;

/** encapsulates a name and transportAddress. */
public class NameAndTransportAddress {
	public final String name;
	public final TransportAddress transportAddress;
	
	public NameAndTransportAddress( String name, TransportAddress transportAddress ) {
		this.name = name;
		this.transportAddress = transportAddress;
	}
	
	public String toString() {
		return "[" + name + ", " + transportAddress + "]" ;
	}
}
