package com.xonami.javaBells;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;

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
import org.jitsi.service.neomedia.format.MediaFormatFactory;

public class JingleMediaStream {
	private static final DynamicPayloadTypeRegistry dynamicPayloadTypes = new DynamicPayloadTypeRegistry();
	
	private final CreatorEnum creator;
	
	private final TreeMap<String,MediaDevice> devices = new TreeMap<String,MediaDevice>();
	
	public JingleMediaStream(CreatorEnum creator) {
		this.creator = creator;
	}
	
	public boolean addDefaultMedia( MediaType mediaType, String name ) {
		MediaService mediaService = LibJitsi.getMediaService();
		MediaDevice dev = mediaService.getDefaultDevice(mediaType, MediaUseCase.CALL);
		
		if( dev == null )
			return false;
		
		devices.put(name, dev);
		return true;
	}
	
	public List<ContentPacketExtension> createContentList(SendersEnum senders) {
		List<ContentPacketExtension> contentList = new ArrayList<ContentPacketExtension>();
		for( Map.Entry<String,MediaDevice> e : devices.entrySet() ) {
			String name = e.getKey();
			MediaDevice dev = e.getValue();

			List<MediaFormat> formats = dev.getSupportedFormats();
			ContentPacketExtension content = new ContentPacketExtension();
	        RtpDescriptionPacketExtension description = new RtpDescriptionPacketExtension();

	        // fill in the basic content:
	        content.setCreator(creator);
	        content.setName(name);
	        if(senders != null && senders != SendersEnum.both)
	            content.setSenders(senders);

	        //RTP description
	        content.addChildExtension(description);
	        description.setMedia(formats.get(0).getMediaType().toString());

	        //now fill in the RTP description
	        for(MediaFormat fmt : formats)
	            description.addPayloadType( formatToPayloadType(fmt, dynamicPayloadTypes));
	        
	        contentList.add(content);
		}
		return contentList;
	}
	
	public void startConnection( NameAndTransportAddress nta, int localRtpPort, int localRtcpPort ) throws IOException {
		MediaDevice dev = devices.get(nta.name);
		
		
		
		MediaService mediaService = LibJitsi.getMediaService();
		
        MediaStream mediaStream = mediaService.createMediaStream(dev);

        mediaStream.setDirection(MediaDirection.SENDRECV);

        // format
        String encoding;
        double clockRate;
        /*
         * The AVTransmit2 and AVReceive2 examples use the H.264 video
         * codec. Its RTP transmission has no static RTP payload type number
         * assigned.   
         */
        byte dynamicRTPPayloadType;

        //FIXME: this should be passed as an argument or something
        switch (dev.getMediaType())
        {
        case AUDIO:
            encoding = "PCMU";
            clockRate = 8000;
            /* PCMU has a static RTP payload type number assigned. */
            dynamicRTPPayloadType = -1;
            break;
        case VIDEO:
            encoding = "H264";
            clockRate = MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED;
            /*
             * The dymanic RTP payload type numbers are usually negotiated
             * in the signaling functionality.
             */
            dynamicRTPPayloadType = 99;
            break;
        default:
            encoding = null;
            clockRate = MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED;
            dynamicRTPPayloadType = -1;
        }

        if (encoding != null)
        {
            MediaFormat format
                = mediaService.getFormatFactory().createMediaFormat(
                        encoding,
                        clockRate);

            /*
             * The MediaFormat instances which do not have a static RTP
             * payload type number association must be explicitly assigned
             * a dynamic RTP payload type number.
             */
            if (dynamicRTPPayloadType != -1)
            {
                mediaStream.addDynamicRTPPayloadType(
                        dynamicRTPPayloadType,
                        format);
            }

            mediaStream.setFormat(format);
        }

        // connector
        int port = nta.transportAddress.getPort();

        StreamConnector connector = new DefaultStreamConnector( new DatagramSocket(localRtpPort), new DatagramSocket(localRtcpPort) );

        mediaStream.setConnector(connector);

        mediaStream.setTarget( new MediaStreamTarget(
        		new InetSocketAddress(nta.transportAddress.getAddress(), nta.transportAddress.getPort()  ),
        		new InetSocketAddress(nta.transportAddress.getAddress(), nta.transportAddress.getPort()+1) ) );

        mediaStream.setName(nta.name);
	}
	
	@Deprecated
	public static List<ContentPacketExtension> createContentList(MediaType mediaType, CreatorEnum creator, String contentName, SendersEnum senders) {
//		List<ContentPacketExtension> mediaDescs = new ArrayList<ContentPacketExtension>();
		
		MediaService mediaService = LibJitsi.getMediaService();

		MediaDevice dev = mediaService.getDefaultDevice(mediaType, MediaUseCase.CALL);
		
		List<MediaFormat> formats = dev.getSupportedFormats();
		
//		MediaDirection direction = MediaDirection.SENDRECV;
//		
//		List<RTPExtension> extensions = dev.getSupportedExtensions();
		
		
        ContentPacketExtension content = new ContentPacketExtension();
        RtpDescriptionPacketExtension description = new RtpDescriptionPacketExtension();

        // fill in the basic content:
        content.setCreator(creator);
        content.setName(contentName);
        if(senders != null && senders != SendersEnum.both)
            content.setSenders(senders);

        //RTP description
        content.addChildExtension(description);
        description.setMedia(formats.get(0).getMediaType().toString());

        //now fill in the RTP description
        for(MediaFormat fmt : formats)
        {
            description.addPayloadType( formatToPayloadType(fmt, dynamicPayloadTypes));
        }

		
		List<ContentPacketExtension> contentList = new ArrayList<ContentPacketExtension>();
		contentList.add(content);
		return contentList;

		// code from CallPeerMediaHandlerJabberImpl.createContentList
	}
	
    public static PayloadTypePacketExtension formatToPayloadType(
            MediaFormat format,
            DynamicPayloadTypeRegistry ptRegistry)
    {
        PayloadTypePacketExtension ptExt = new PayloadTypePacketExtension();

        int payloadType = format.getRTPPayloadType();

        if (payloadType == MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN)
                payloadType = ptRegistry.obtainPayloadTypeNumber(format);

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
    
    /** Checks the content packet of the jingle iq and returns its name. If there
     * is a problem with the formatting of the content packet or jingle IQ an
     * IOException is thrown.
     * 
     * @param jiq
     * @return the name of the contentpacket
     * @throws IOException if the name cannot be found
     */
	public static String getContentPacketName(JingleIQ jiq) throws IOException {
		String name = null;
		List<ContentPacketExtension> cpes = jiq.getContentList();
		for( ContentPacketExtension cpe : cpes ) {
			if( name != null )
				throw new IOException();
			name = cpe.getName();
		}
		if( name == null )
			throw new IOException();
		return name;
	}
}