package com.xonami.javaBells;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ch.imvs.sdes4j.srtp.SrtpCryptoAttribute;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;

import org.ice4j.TransportAddress;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;

public class JingleStreamManager {
	private static final DynamicPayloadTypeRegistry dynamicPayloadTypes = new DynamicPayloadTypeRegistry();

	private final CreatorEnum creator;
	private SendersEnum senders;

	private final Map<String,MediaDevice> devices = new ConcurrentHashMap<String,MediaDevice>();
	private final Map<String,JingleStream> jingleStreams = new ConcurrentHashMap<String,JingleStream>();
	private final Map<String,MediaFormat> streamNameToMediaFormats = new ConcurrentHashMap<String,MediaFormat>();
	private final Map<String,Byte> streamNameToPayloadTypeId = new ConcurrentHashMap<String,Byte>();
    private final Map<String, SDesControl> streamNameToSDesControl = new ConcurrentHashMap<String, SDesControl>();

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
		this.senders = senders;
		List<ContentPacketExtension> contentList = new ArrayList<ContentPacketExtension>();
		for( Map.Entry<String,MediaDevice> e : devices.entrySet() ) {
			String name = e.getKey();
			MediaDevice dev = e.getValue();
			contentList.add( createContentPacketExtention( senders, name, dev, null, MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN ) );
		}
		return contentList;
	}

	private ContentPacketExtension createContentPacketExtention(SendersEnum senders, String name, MediaDevice dev, MediaFormat fmt, int payloadId ) {
		this.senders = senders;
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

	public JingleStream startStream(String name, IceAgent iceAgent ) throws IOException {
		if( streamNameToMediaFormats.size() == 0 ) {
			// media has not been negotiated. This seems to happen with jitsi.
			// we will assume our requested formats are acceptable.
			parseIncomingAndBuildMedia( createContentList( senders ), senders );
		}

        IceMediaStream stream = iceAgent.getAgent().getStream(name);
        MediaFormat format = streamNameToMediaFormats.get(name);
        Byte payloadTypeId = streamNameToPayloadTypeId.get(name);
        if( stream == null || format == null || payloadTypeId == null )
        	throw new IOException("Stream \"" + name + "\" not found.");
        Component rtpComponent = stream.getComponent(org.ice4j.ice.Component.RTP);
        Component rtcpComponent = stream.getComponent(org.ice4j.ice.Component.RTCP);

        if( rtpComponent == null )
        	throw new IOException("RTP component not found.");
        if( rtcpComponent == null )
        	throw new IOException("RTCP Component not found.");

        CandidatePair rtpPair = rtpComponent.getSelectedPair();
        CandidatePair rtcpPair = rtcpComponent.getSelectedPair();

//        System.out.println( "RTP : L " + rtpPair.getLocalCandidate().getDatagramSocket().getLocalPort() + " <-> " + rtpPair.getRemoteCandidate().getTransportAddress() + " R " );
//        System.out.println( "RTCP: L " + rtcpPair.getLocalCandidate().getDatagramSocket().getLocalPort() + " <-> " + rtcpPair.getRemoteCandidate().getTransportAddress() + " R " );

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

        MediaStream mediaStream = mediaService.createMediaStream(
                new DefaultStreamConnector(rtpDatagramSocket, rtcpDatagramSocket), dev,
                streamNameToSDesControl.get(name));

        mediaStream.setDirection(MediaDirection.SENDONLY);

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

        ptExt.setClockrate(String.valueOf(Double.valueOf(format.getClockRate()).intValue()));

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
		return parseIncomingAndBuildMedia(jiq,senders, true, true);
	}
    public List<ContentPacketExtension> parseIncomingAndBuildMedia(JingleIQ jiq, SendersEnum senders, boolean audio, boolean video) throws IOException {
        return parseIncomingAndBuildMedia(jiq.getContentList(),senders, audio, video);
    }
    public List<ContentPacketExtension> parseIncomingAndBuildMedia(List<ContentPacketExtension> cpes, SendersEnum senders) throws IOException {
        return parseIncomingAndBuildMedia(cpes, senders, true, true);
    }
	public List<ContentPacketExtension> parseIncomingAndBuildMedia(List<ContentPacketExtension> cpes, SendersEnum senders, boolean audio, boolean video) throws IOException {
		this.senders = senders;
		String name = null;
		String toclean = null;
		List<ContentPacketExtension> ret = new ArrayList<ContentPacketExtension>();
            for (ContentPacketExtension cpe : cpes) {
                try {
                    toclean = name = cpe.getName();
                    if (name == null)
                        throw new IOException();

                    String media = null;

                    List<RtpDescriptionPacketExtension> descriptions = cpe.getChildExtensionsOfType(RtpDescriptionPacketExtension.class);

                    for (RtpDescriptionPacketExtension description : descriptions) {
                        media = description.getMedia();
                        boolean mediaAdded = false;
                        if (audio && "audio".equals(media)) {
                            mediaAdded = addDefaultMedia(MediaType.AUDIO, name);
                        } else if (video && "video".equals(media)) {
                            mediaAdded = addDefaultMedia(MediaType.VIDEO, name);
                        }
                        if (mediaAdded) {
                            List<PayloadTypePacketExtension> payloadTypes = description.getPayloadTypes();
                            for (PayloadTypePacketExtension payloadType : payloadTypes) {
                                MediaFormat mf = getSupportedFormat(name, payloadType);
                                if (mf == null)
                                    continue; //no match
                                final ContentPacketExtension cpeResponse =
                                        createContentPacketExtention(senders, name, devices.get(name), mf,
                                                payloadType.getID());
                                addEncryption(name, description,
                                        (RtpDescriptionPacketExtension) cpeResponse.getChildExtensions().get(0));
                                ret.add(cpeResponse);
                                toclean = null;
                                streamNameToMediaFormats.put(name, mf);
                                streamNameToPayloadTypeId.put(name, (byte) payloadType.getID());
                                break; //stop on first match
                            }
                        }
                    }
                    if (media == null)
                        throw new IOException();
                } finally {
                    if (toclean != null)
                        devices.remove(toclean);
                }
            }
            if( ret.size() == 0 )
				return null;
			return ret;
	}

	private MediaFormat getSupportedFormat( String name, PayloadTypePacketExtension payloadType ) {
		MediaDevice dev = devices.get(name);
        MediaType mediaType = dev.getMediaType();

		for( MediaFormat mf : dev.getSupportedFormats() ) {
//			if( ( mf.getRTPPayloadType() == MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN || mf.getRTPPayloadType() == payloadType.getID() ) //FIXME: will this work for locally defined ids?
//					&& mf.getClockRateString().equals( String.valueOf(payloadType.getClockrate())) //FIXME: does the clockrate really need to match? will the device report all available clock rates?
//					&& mf.getEncoding().equals(payloadType.getName()) ) {
				//FIXME: we should probably check advanced attributes and format parameters, but my guess is
				// that in most cases we can adapt.
            final String clockrate = payloadType.getClockrate();
            final String[] split = clockrate.split("/");
            if(split.length > 0){
            if (mf.matches(mediaType, payloadType.getName(), Double.valueOf(split[0]), payloadType.getChannels(), null)) {//formatParameters is not used by default
				return mf;
			}}
		}
		return null;
	}

    public void closeStreams() {
        for (JingleStream jingleStream : jingleStreams.values()) {
            jingleStream.shutdown();
        }
    }

    private RtpDescriptionPacketExtension addEncryption(String streamName, RtpDescriptionPacketExtension offer,
                                                        RtpDescriptionPacketExtension response) {
        final EncryptionPacketExtension encryptionOffer = offer.getEncryption();
        if (encryptionOffer != null) {
            final SDesControl sDesControl =
                    (SDesControl) LibJitsi.getMediaService().createSrtpControl(SrtpControlType.SDES);
//            sDesControl.getInitiatorCryptoAttributes();
            List<CryptoPacketExtension> cryptoPacketExtensions
                    = encryptionOffer.getCryptoList();
            List<SrtpCryptoAttribute> peerAttributes
                    = new ArrayList<SrtpCryptoAttribute>(cryptoPacketExtensions.size());

            for (CryptoPacketExtension cpe : cryptoPacketExtensions) {
                peerAttributes.add(cpe.toSrtpCryptoAttribute());
            }
            final SrtpCryptoAttribute srtpCryptoAttribute = sDesControl.responderSelectAttribute(peerAttributes);
            if (srtpCryptoAttribute != null) {
                final EncryptionPacketExtension encryptionResponse = new EncryptionPacketExtension();
                encryptionResponse.addChildExtension(new CryptoPacketExtension(srtpCryptoAttribute));
                response.setEncryption(encryptionResponse);
                response.setAttribute("profile", "RTP/SAVPF");
                streamNameToSDesControl.put(streamName, sDesControl);
            }
        }
        return response;
    }
}