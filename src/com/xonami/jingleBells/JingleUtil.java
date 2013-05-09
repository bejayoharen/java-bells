package com.xonami.jingleBells;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;

import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.MediaUseCase;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;

public class JingleUtil {
	private static final DynamicPayloadTypeRegistry dynamicPayloadTypes = new DynamicPayloadTypeRegistry();
	
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