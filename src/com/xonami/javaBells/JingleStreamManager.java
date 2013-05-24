package com.xonami.javaBells;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;

import org.ice4j.TransportAddress;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.DefaultStreamConnector;
import org.jitsi.service.neomedia.MediaDirection;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.MediaUseCase;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;

public class JingleStreamManager {
	private static final DynamicPayloadTypeRegistry dynamicPayloadTypes = new DynamicPayloadTypeRegistry();
	
	private final CreatorEnum creator;
	
	private final TreeMap<String,MediaDevice> devices = new TreeMap<String,MediaDevice>();
	private final TreeMap<String,JingleStream> jingleStreams = new TreeMap<String,JingleStream>();
	private final TreeMap<String,MediaFormat> streamNameToMediaFormats = new TreeMap<String,MediaFormat>();
	private final TreeMap<String,Byte> streamNameToPayloadTypeId = new TreeMap<String,Byte>();
	
	public JingleStreamManager(CreatorEnum creator) {
		this.creator = creator;
	}

	public Set<String> getMediaNames() {
		return Collections.unmodifiableSet( devices.keySet() );
	}
	
	public boolean addDefaultMedia( MediaType mediaType, String name ) {
		MediaService mediaService = LibJitsi.getMediaService();
		MediaDevice dev = mediaService.getDefaultDevice(mediaType, MediaUseCase.CALL);
		
		if( dev == null )
			return false;
		
		devices.put(name, dev);
		return true;
	}
	
	public MediaDevice getDefaultAudioDevice() {
		MediaService mediaService = LibJitsi.getMediaService();
		MediaDevice dev = mediaService.getDefaultDevice(MediaType.AUDIO, MediaUseCase.CALL);
		return dev;
	}
	
	public List<ContentPacketExtension> createContentList(SendersEnum senders) {
		List<ContentPacketExtension> contentList = new ArrayList<ContentPacketExtension>();
		for( Map.Entry<String,MediaDevice> e : devices.entrySet() ) {
			String name = e.getKey();
			MediaDevice dev = e.getValue();
			contentList.add( createContentPacketExtention( senders, name, dev, null, MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN ) );
		}
		return contentList;
	}

	private ContentPacketExtension createContentPacketExtention(SendersEnum senders, String name, MediaDevice dev, MediaFormat fmt, int payloadId ) {
		ContentPacketExtension content = new ContentPacketExtension();
		RtpDescriptionPacketExtension description = new RtpDescriptionPacketExtension();

		// fill in the basic content:
		content.setCreator(creator);
		content.setName(name);
		if (senders != null && senders != SendersEnum.both)
			content.setSenders(senders);

		// RTP description
		content.addChildExtension(description);

		// now fill in the RTP description
		if (fmt == null) {
			List<MediaFormat> formats = dev.getSupportedFormats();
			description.setMedia(formats.get(0).getMediaType().toString());
			for (MediaFormat mf : formats)
				description.addPayloadType(formatToPayloadType(mf, dynamicPayloadTypes, payloadId));
		} else {
			description.setMedia(fmt.getMediaType().toString());
			description.addPayloadType(formatToPayloadType(fmt, dynamicPayloadTypes, payloadId));
		}

		return content;
	}
	
	public JingleStream startStream(String name, IceAgent iceAgent) throws IOException {
        IceMediaStream stream = iceAgent.getAgent().getStream(name);
        MediaFormat format = streamNameToMediaFormats.get(name);
        Byte payloadTypeId = streamNameToPayloadTypeId.get(name);
        if( stream == null || format == null || payloadTypeId == null )
        	throw new IOException("Stream not found.");
        Component rtpComponent = stream.getComponent(org.ice4j.ice.Component.RTP);
        Component rtcpComponent = stream.getComponent(org.ice4j.ice.Component.RTCP);
        
        if( rtpComponent == null )
        	throw new IOException("RTP component not found.");
        if( rtcpComponent == null )
        	throw new IOException("RTCP Component not found.");

        CandidatePair rtpPair = rtpComponent.getSelectedPair();
        CandidatePair rtcpPair = rtcpComponent.getSelectedPair();
        
        //FIXME:
        System.out.println( "RTP : L " + rtpPair.getLocalCandidate().getDatagramSocket().getLocalPort() + " <-> " + rtpPair.getRemoteCandidate().getTransportAddress() + " R " );
        System.out.println( "RTCP: L " + rtcpPair.getLocalCandidate().getDatagramSocket().getLocalPort() + " <-> " + rtcpPair.getRemoteCandidate().getTransportAddress() + " R " );
        
        return startStream( name,
        		payloadTypeId,
        		format,
        		rtpPair.getRemoteCandidate().getTransportAddress(),
        		rtcpPair.getRemoteCandidate().getTransportAddress(),
        		rtpPair.getLocalCandidate().getDatagramSocket(),
        		rtcpPair.getLocalCandidate().getDatagramSocket());
	}
	
	public JingleStream startStream( String name, byte payloadTypeId, MediaFormat format, TransportAddress remoteRtpAddress, TransportAddress remoteRtcpAddress, DatagramSocket rtpDatagramSocket, DatagramSocket rtcpDatagramSocket ) throws IOException {
		MediaDevice dev = devices.get(name);
		
		MediaService mediaService = LibJitsi.getMediaService();
		
        MediaStream mediaStream = mediaService.createMediaStream(dev);

        mediaStream.setDirection(MediaDirection.SENDRECV);

        /*
         * The MediaFormat instances which do not have a static RTP
         * payload type number association must be explicitly assigned
         * a dynamic RTP payload type number.
         */
        if ( format.getRTPPayloadType() == MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN ) {
            mediaStream.addDynamicRTPPayloadType(
                    payloadTypeId,
                    format);
        }
        
//        System.out.println( "++++++++++++++++++++++" );
//        System.out.println( "++++++++++++++++++++++" );
//        System.out.println( "++++++++++++++++++++++" );
//        System.out.println( "For stream: " + name );
//        System.out.println( "Format: " + format );
//        System.out.println( "Dynamic payload type: " + payloadTypeId );
//        System.out.println( "++++++++++++++++++++++" );
//        System.out.println( "++++++++++++++++++++++" );
//        System.out.println( "++++++++++++++++++++++" );

        mediaStream.setFormat(format);

        StreamConnector connector = new DefaultStreamConnector( rtpDatagramSocket, rtcpDatagramSocket );

        mediaStream.setConnector(connector);

        mediaStream.setTarget( new MediaStreamTarget( remoteRtpAddress, remoteRtcpAddress ) );

        mediaStream.setName(name);
        
        mediaStream.start();
        
        JingleStream js = new JingleStream( name, mediaStream, this );
        jingleStreams.put( name, js );
        return js;
	}
	
    public static PayloadTypePacketExtension formatToPayloadType(
            MediaFormat format,
            DynamicPayloadTypeRegistry ptRegistry,
            int payloadId)
    {
        PayloadTypePacketExtension ptExt = new PayloadTypePacketExtension();

        int payloadType = format.getRTPPayloadType();

        if (payloadType == MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN) {
        	if( payloadId != MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN )
        		payloadType = payloadId;
        	else
                payloadType = ptRegistry.obtainPayloadTypeNumber(format);
        }

        ptExt.setId(payloadType);
        ptExt.setName(format.getEncoding());

        if(format instanceof AudioMediaFormat)
            ptExt.setChannels(((AudioMediaFormat)format).getChannels());

        ptExt.setClockrate((int)format.getClockRate());

        /*
         * Add the format parameters and the advanced attributes (as parameter
         * packet extensions).
         */
        for(Map.Entry<String, String> entry :
            format.getFormatParameters().entrySet())
        {
            ParameterPacketExtension ext = new ParameterPacketExtension();
            ext.setName(entry.getKey());
            ext.setValue(entry.getValue());
            ptExt.addParameter(ext);
        }
        for(Map.Entry<String, String> entry :
            format.getAdvancedAttributes().entrySet())
        {
            ParameterPacketExtension ext = new ParameterPacketExtension();
            ext.setName(entry.getKey());
            ext.setValue(entry.getValue());
            ptExt.addParameter(ext);
        }

        return ptExt;
    }
    
    /** Parses the incoming session-initiate or session-accept jingle iqs and
     * and sets up a local stream as required by building the required media.
     * Returns a new list of ContentPacketExtention representing the content
     * of the stream. Returns null if no compatible stream was found.
     * @param jiq the jingle IQ containing the request to parse
     * @param senders who is sending and receiving media?
     * @throws IOException if the packets are not correctly parsed. */
	public List<ContentPacketExtension> parseIncomingAndBuildMedia(JingleIQ jiq, SendersEnum senders) throws IOException {
		String name = null;
		String toclean = null;
		List<ContentPacketExtension> cpes = jiq.getContentList();
		List<ContentPacketExtension> ret = new ArrayList<ContentPacketExtension>();
		try {
			for( ContentPacketExtension cpe : cpes ) {
				toclean = name = cpe.getName();
				if( name == null )
					throw new IOException();
				
//				SendersEnum senders = cpe.getSenders();
//				CreatorEnum creator = cpe.getCreator();
				String media = null;
				
				List<RtpDescriptionPacketExtension> descriptions = cpe.getChildExtensionsOfType(RtpDescriptionPacketExtension.class);
				
				for( RtpDescriptionPacketExtension description : descriptions ) {
					media = description.getMedia();
					if( "audio".equals(media) ) {
						if( !addDefaultMedia( MediaType.AUDIO, name ) )
							throw new IOException( "Could not create audio device" );
					} else if( "video".equals(media) ) {
						if( !addDefaultMedia( MediaType.VIDEO, name ) )
							throw new IOException( "Could not create video device" );
					} else {
						throw new IOException( "Unknown media type: " + media );
					}
					List<PayloadTypePacketExtension> payloadTypes = description.getPayloadTypes();
					for( PayloadTypePacketExtension payloadType : payloadTypes ) {
						MediaFormat mf = getSupportedFormat( name, payloadType );
						if( mf == null )
							continue; //no match
						ret.add( createContentPacketExtention( senders, name, devices.get(name), mf, payloadType.getID() ) );
						toclean = null;
						streamNameToMediaFormats.put( name, mf );
						streamNameToPayloadTypeId.put( name, (byte) payloadType.getID() );
						break; //stop on first match
					}
				}
				if( media == null )
					throw new IOException();
			}
			if( ret.size() == 0 )
				return null;
			return ret;
		} finally {
			if( toclean != null )
				devices.remove(toclean);
		}
	}
	private MediaFormat getSupportedFormat( String name, PayloadTypePacketExtension payloadType ) {
		MediaDevice dev = devices.get(name);

		for( MediaFormat mf : dev.getSupportedFormats() ) {
			if( ( mf.getRTPPayloadType() == MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN || mf.getRTPPayloadType() == payloadType.getID() ) //FIXME: will this work for locally defined ids?
					&& mf.getClockRateString().equals( String.valueOf(payloadType.getClockrate())) //FIXME: does the clockrate really need to match? will the device report all available clock rates?
					&& mf.getEncoding().equals(payloadType.getName()) ) {
				//FIXME: we should probably check advanced attributes and format parameters, but my guess is
				// that in most cases we can adapt.
				return mf;
			}
		}
		return null;
	}

}