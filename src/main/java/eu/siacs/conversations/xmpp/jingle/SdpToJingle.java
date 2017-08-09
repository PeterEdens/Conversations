package eu.siacs.conversations.xmpp.jingle;


import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;

/**
 * Created by petere on 7/13/2017.
 */

class SdpToJingle {

    /**
     * New line constant character.
     */
    private static final String NL = "\r\n";

    public static String sdpFromJingle(JinglePacket packet) {
        String result = "";

        Content content = packet.getJingleContent();
        StringBuilder sb = new StringBuilder();

        sb.append("v=0").append(NL);
        sb.append("o=- 1923518516 2 IN IP4 0.0.0.0").append(NL);
        sb.append("s=-").append(NL);
        sb.append("t=0 0").append(NL);
        sb.append("a=group:BUNDLE audio").append(NL);

        Element description = content.findChild("description", "urn:xmpp:jingle:apps:rtp:1");
        Element source = description.findChild("source", "urn:xmpp:jingle:apps:rtp:ssma:0");
        List<Element> parameters = source.getChildren();

        String msid = "";
        for (Element elem: parameters) {
            if (elem.getAttribute("name").equals("msid")) {
                msid = elem.getAttribute("value");
                msid = msid.substring(0, msid.indexOf(' '));
            }
        }
        sb.append("a=msid-semantic: WMS ").append(msid).append(NL);

        String media = description.getAttribute("media");
        sb.append("m=" + media + " 9 UDP/TLS/RTP/SAVPF");
        List<Element> children = description.getChildren();
        for (Element elem: children) {
            if (elem.getName().equals("payload-type")) {
                sb.append(" ");
                sb.append(elem.getAttribute("id"));
            }
        }
        sb.append(NL);

        sb.append("c=IN IP4 0.0.0.0").append(NL);
        sb.append("a=rtcp:9 IN IP4 0.0.0.0").append(NL);

        Element transport = content.udpTransport();

        sb.append("a=ice-ufrag:").append(transport.getAttribute("ufrag")).append(NL);
        sb.append("a=ice-pwd:").append(transport.getAttribute("pwd")).append(NL);
        sb.append("a=ice-options:renomination").append(NL);

        Element dtls = transport.findChild("fingerprint");
        sb.append("a=fingerprint:").append(dtls.getAttribute("hash")).append(' ').append(dtls.getContent()).append(NL);
        if (dtls.getAttribute("setup") != null) {
            sb.append("a=setup:" + dtls.getAttribute("setup")).append(NL);
        }
        sb.append("a=mid:audio").append(NL);
        sb.append("a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level").append(NL);
        sb.append("a=sendrecv").append(NL);
        sb.append("a=rtcp-mux").append(NL);

        for (Element elem: children) {
            if (elem.getName().equals("payload-type")) {
                sb.append("a=rtpmap:" + elem.getAttribute("id")).append(" ");
                sb.append(elem.getAttribute("name") + "/" + elem.getAttribute("clockrate"));
                if (elem.getAttribute("channels").equals("2")) {
                    sb.append("/" + elem.getAttribute("channels"));
                }
                sb.append(NL);

                List<Element> formatChildren = elem.getChildren();
                if (formatChildren.size() != 0) {
                    String fmtp = "a=fmtp:" + elem.getAttribute("id") + " ";
                    boolean added = false;
                    for (Element childElem : formatChildren) {
                        if (childElem.getName().equals("parameter")) {
                            if (added) {
                                fmtp += ";";
                            }
                            fmtp += childElem.getAttribute("name") + "=" + childElem.getAttribute("value");
                            added = true;
                        } else {
                            sb.append("a=" + childElem.getName() + ":" + elem.getAttribute("id") + " " + childElem.getAttribute("type"));
                            sb.append(NL);
                        }
                    }
                    sb.append(fmtp);
                    sb.append(NL);
                }
            }
        }

        for (Element elem: parameters) {
            sb.append("a=ssrc:" + source.getAttribute("ssrc")).append(" ");
            sb.append(elem.getAttribute("name") + ":" + elem.getAttribute("value")).append(NL);
        }

        return sb.toString();
    }

    public static Content jingleFromSdp(Content content, SessionDescription sdp, ArrayList<IceCandidate> candidates) {

        content.setAttribute("senders", "both");
        content.setAttribute("xmlns", "urn:xmpp:jingle:1");
        Element description = content.addChild("description", "urn:xmpp:jingle:apps:rtp:1");

        String[] lines = sdp.description.split("\r\n");
        for (String line: lines) {
            if (line.startsWith("m=audio")) {
                description.setAttribute("media", "audio");
            }
            else if (line.startsWith("m=video")) {
                break;
            }
            else if (line.startsWith("a=ssrc:")) {
                description.setAttribute("ssrc", line.substring(7, line.indexOf(' ')));
                Element source = description.findChild("source");

                if (source == null) {
                    source = description.addChild("source", "urn:xmpp:jingle:apps:rtp:ssma:0");
                }

                source.setAttribute("ssrc", line.substring(line.indexOf(":") + 1, line.indexOf(' ')));
                if (line.contains("cname")) {
                    Element parameter = source.addChild("parameter", "urn:xmpp:jingle:apps:rtp:ssma:0");
                    parameter.setAttribute("name", "cname");
                    parameter.setAttribute("value", line.substring(line.indexOf("cname:") + 6));
                }
                else if (line.contains("msid")) {
                    Element parameter = source.addChild("parameter", "urn:xmpp:jingle:apps:rtp:ssma:0");
                    parameter.setAttribute("name", "msid");
                    parameter.setAttribute("value", line.substring(line.indexOf("msid:") + 5));
                }
            }
            else if (line.startsWith("a=rtpmap:")) {
                Element payloadType = description.addChild("payload-type", "urn:xmpp:jingle:apps:rtp:1");
                String id = line.substring(9, line.indexOf(' '));
                String name = line.substring(line.indexOf(' ') + 1);
                String[] parts = name.split("/");
                name = parts[0];
                String clockrate = parts[1];
                String channels = "1";
                if (parts.length == 3) {
                    channels = parts[2];
                }
                payloadType.setAttribute("id", id);
                payloadType.setAttribute("name", name);
                payloadType.setAttribute("clockrate", clockrate);
                payloadType.setAttribute("channels", channels);
            }
            else if (line.startsWith("a=rtcp-fb:")) {
                String id = line.substring(10, line.indexOf(' '));
                List<Element> children = description.getChildren();
                for (Element child: children) {
                    if (child.getName().equals("payload-type") && child.getAttribute("id").equals(id)) {
                        Element rtcp = child.addChild("rtcp-fb", "urn:xmpp:jingle:apps:rtp:rtcp-fb:0");
                        rtcp.setAttribute("type", line.substring(line.indexOf(" ") + 1));
                    }
                }
            }
            else if (line.startsWith("a=fmtp:")) {
                String id = line.substring(7, line.indexOf(' '));
                String value = line.substring(line.indexOf(' ') + 1);
                List<Element> children = description.getChildren();
                for (Element child: children) {
                    if (child.getName().equals("payload-type") && child.getAttribute("id").equals(id)) {
                        Element parameter = child.addChild("parameter", "urn:xmpp:jingle:apps:rtp:1");
                        parameter.setAttribute("name", value.substring(0, value.indexOf('=')));
                        if (value.indexOf(';') != -1) {
                            parameter.setAttribute("value", value.substring(value.indexOf("=") + 1, value.indexOf(';')));
                        }
                        else {
                            parameter.setAttribute("value", value.substring(value.indexOf("=") + 1));
                        }
                        String nextPart = value.substring(value.indexOf(";") + 1);
                        parameter = child.addChild("parameter", "urn:xmpp:jingle:apps:rtp:1");
                        parameter.setAttribute("name", nextPart.substring(0, nextPart.indexOf('=')));
                        parameter.setAttribute("value", nextPart.substring(nextPart.indexOf("=") + 1));
                    }
                }
            }
            else if (line.startsWith("a=rtcp-mux")) {
                description.addChild("rtcp-mux", "urn:xmpp:jingle:apps:rtp:1");
            }
            else if (line.startsWith("a=ice-ufrag:")) {
                Element transport = content.findChild("transport");
                if (transport == null) {
                    transport = content.addChild("transport", "urn:xmpp:jingle:transports:ice-udp:1");
                }
                transport.setAttribute("ufrag", line.substring(line.indexOf("ufrag:") + 6));
            }
            else if (line.startsWith("a=ice-pwd:")) {
                Element transport = content.findChild("transport");
                if (transport == null) {
                    transport = content.addChild("transport", "urn:xmpp:jingle:transports:ice-udp:1");
                }
                transport.setAttribute("pwd", line.substring(line.indexOf("pwd:") + 6));
            }
            else if (line.startsWith("a=fingerprint")) {
                Element transport = content.findChild("transport");
                if (transport == null) {
                    transport = content.addChild("transport", "urn:xmpp:jingle:transports:ice-udp:1");
                }
                Element fingerprint = transport.addChild("fingerprint", "urn:xmpp:jingle:apps:dtls:0");
                fingerprint.setAttribute("hash", line.substring(line.indexOf(":") + 1, line.indexOf(' ')));
                fingerprint.setContent(line.substring(line.indexOf(' ') + 1));
            }
            else if (line.startsWith("a=setup:")) {
                Element transport = content.findChild("transport");
                if (transport == null) {
                    transport = content.addChild("transport", "urn:xmpp:jingle:transports:ice-udp:1");
                }
                Element fingerprint = transport.findChild("fingerprint");
                fingerprint.setAttribute("setup", "actpass");
            }
        }

        Element transport = content.findChild("transport");

        if (transport != null) {
            for (IceCandidate candidate: candidates) {
                Element candidateElem = transport.addChild("candidate");
                String candidateSdp = candidate.sdp;
                candidateSdp = candidateSdp.substring(candidateSdp.indexOf(':') + 1);
                String[] parts = candidateSdp.split(" ");

                int index = 0;
                candidateElem.setAttribute("foundation", "1");
                candidateElem.setAttribute("id", parts[index++]);
                candidateElem.setAttribute("component", parts[index++]);
                candidateElem.setAttribute("protocol", parts[index++].toLowerCase());
                candidateElem.setAttribute("priority", parts[index++]);
                candidateElem.setAttribute("ip", parts[index++]);
                candidateElem.setAttribute("port", parts[index++]);

                // name value pairs
                for (int idx = index; idx < parts.length; idx++) {

                    if (parts[idx].equals("typ")) {

                        candidateElem.setAttribute("type", parts[idx + 1]);
                    }
                    else if (parts[idx].equals("raddr")) {
                        candidateElem.setAttribute("rel-addr", parts[idx + 1]);
                    }
                    else if (parts[idx].equals("rport")) {
                        candidateElem.setAttribute("rel-port", parts[idx + 1]);
                    }
                    else if (parts[idx].equals("generation")) {
                        candidateElem.setAttribute("generation", parts[idx + 1]);
                    }
                    else if (parts[idx].equals("network-id")) {
                        candidateElem.setAttribute("network", parts[idx + 1]);
                    }
                }
            }
        }
        return content;
    }

    public static IceCandidate candidateFromJingle(JinglePacket packet) {
        Content content = packet.getJingleContent();
        Element transport = content.findChild("transport");
        Element candidate = transport.findChild("candidate");

        String mid = content.getAttribute("name");
        StringBuilder sb = new StringBuilder();

        sb.append("candidate:");
        sb.append(candidate.getAttribute("id"));
        sb.append(" " + candidate.getAttribute("component"));
        sb.append(" " + candidate.getAttribute("protocol"));
        sb.append(" " + candidate.getAttribute("priority"));
        sb.append(" " + candidate.getAttribute("ip"));
        sb.append(" " + candidate.getAttribute("port"));
        sb.append(" typ " + candidate.getAttribute("type"));
        if (candidate.getAttribute("rel-addr") != null && !candidate.getAttribute("rel-addr").equals("0.0.0.0")) {
            sb.append(" raddr " + candidate.getAttribute("rel-addr"));
            sb.append(" rport " + candidate.getAttribute("rel-port"));
        }
        sb.append(" generation " + candidate.getAttribute("generation"));
        sb.append(" ufrag " + transport.getAttribute("ufrag"));
        sb.append(" network-id " + candidate.getAttribute("network"));
        sb.append(" network-cost 10");

        return new IceCandidate(mid, 0, sb.toString());
    }


    /*public static JingleIQ createTransportInfo(String jid, IceCandidate candidate)
    {
        JingleIQ iq = new JingleIQ();
        iq.setAction(JingleAction.TRANSPORT_INFO);
        iq.setTo(jid);
        iq.setType(IQ.Type.SET);

        ContentPacketExtension content
                = new ContentPacketExtension(
                ContentPacketExtension.CreatorEnum.initiator,
                candidate.sdpMid);
        IceUdpTransportPacketExtension transport = new IceUdpTransportPacketExtension();

        CandidatePacketExtension cpe = parseCandidate(candidate.sdp);
        transport.addCandidate(cpe);

        content.addChildExtension(transport);

        iq.addContent(content);

        return iq;
    }

    public static CandidatePacketExtension parseCandidate(String c)
    {
        CandidatePacketExtension cpe = new CandidatePacketExtension();

        //a=candidate:2 1 udp 2130706431 176.31.40.85 10031 typ host generation 0
        //a=candidate:3 1 ssltcp 2113939711 2001:41d0:d:750:0:0:0:9 4443 typ host generation 0

        String[] parts = c.substring(12).split(" ");
        String foundation = parts[0];
        String component = parts[1];
        String protocol = parts[2];
        String priority = parts[3];
        String addr = parts[4];
        String port = parts[5];
        String typ = parts[7];
        String generation = parts[9];

        cpe.setPort(Integer.valueOf(port));
        cpe.setFoundation(foundation);
        cpe.setProtocol(protocol);
        cpe.setPriority(Long.valueOf(priority));
        cpe.setComponent(Integer.valueOf(component));
        cpe.setIP(addr);

        //FIXME: only host is supported
        if ("host".equals(typ))
            cpe.setType(CandidateType.host);

        cpe.setGeneration(Integer.valueOf(generation));

        return cpe;
    }*/
}
