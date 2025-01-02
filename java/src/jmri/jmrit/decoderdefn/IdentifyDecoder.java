package jmri.jmrit.decoderdefn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with a programmer to identify the
 * {@link jmri.jmrit.decoderdefn.DecoderIndexFile} entry for a decoder on the
 * programming track. Create a subclass of this which implements {@link #done}
 * to handle the results of the identification.
 * <p>
 * This is a class (instead of a {@link jmri.jmrit.decoderdefn.DecoderIndexFile}
 * member function) to simplify use of {@link jmri.Programmer} callbacks.
 * <p>
 * Contains manufacturer-specific code to generate a 3rd "productID" identifier,
 * in addition to the manufacturer ID and model ID:<ul>
 * <li>Dietz (mfgID == 115) CV128 is ID</li>
 * <li>DIY: (mfgID == 13) CV47 is the highest byte, CV48 is high byte, CV49 is
 *  low byte, CV50 is the lowest byte; (CV47 == 1) is reserved for the Czech
 *  Republic</li>
 * <li>Doehler &amp; Haass: (mfgID == 97) CV261 is ID from 2020 firmwares</li>
 * <li>ESU: (mfgID == 151, modelID == 255) use RailCom&reg; Product ID CVs;
 *  write {@literal 0=>CV31}, write {@literal 255=>CV32}, then CVs 261 (lowest)
 *  to 264 (highest) are a four byte ID</li>
 * <li>Harman: (mfgID == 98) CV112 is high byte, CV113 is low byte of ID</li>
 * <li>Hornby: (mfgID == 48) <ul>
 *     <li>If CV7 = 254, this is a HN7000 series decoder. The ID is in 
 *         CV200(MSB), CV201 (LSB)
 *     <li>Otherwise CV159 is the ID. If (CV159 == 143), CV159 is
 *         low byte of ID and CV158 is high byte of ID. C159 is not present in some
 *         models, in which case no "productID" can be determined. (This code uses
 *         {@link #setOptionalCv(boolean flag) setOptionalCv()} and
 *         {@link #isOptionalCv() isOptionalCv()} as documented below.)</li>
 *      </ul>
 * <li>QSI: (mfgID == 113) write {@literal 254=>CV49}, write {@literal 4=>CV50},
 *  then CV56 is high byte, write {@literal 5=>CV50}, then CV56 is low byte of
 *  ID</li>
 * <li>SoundTraxx: (mfgID == 141, modelID == 70, 71 or 72) The product ID is made from
 *   <ul>
 *    <li>CV 256 bits 0-7
 *    <li>CV 255 bits 8-10
 *    <li>CV 253 bit 11-18
 *   </ul>
 *   i.e. productID = CV256 | ((CV255 &amp; 7) &lt;&lt; 8) | (CV253 &lt;&lt; 11)
 * </li> 
 * <li>TCS: (mfgID == 153) CV249 is physical hardware id, V5 and above use
 *  CV248, CV110 and CV111 to identify specific sound sets and
 *  features. New productID process triggers if (CV249 &gt; 128).</li>
 * <li>Train-O-Matic: (mfgID == 78) CV508 lowest byte,
 *  CV509 low byte and CV510 high byte</li>
 * <li>Zimo: (mfgID == 145) CV250 is ID</li>
 * </ul>
 * <dl>
 * <dt>Optional CVs:</dt>
 * <dd>
 * Some decoders have CVs that may or may not be present. In this case:
 * <ul>
 * <li>Call {@link #setOptionalCv(boolean flag) setOptionalCv(true)} prior to
 * the {@link #readCV(String cv) readCV(cv)} call.</li>
 * <li>At the next step, check the returned value of
 * {@link #isOptionalCv() isOptionalCv()}. If it is still {@code true}, the CV
 * read failed (despite retries) and the contents of the {@code value} field are
 * undefined. You can either:<br>
 * <ul>
 * <li>{@code return true} to indicate the Identify process has completed
 * successfully without using the failed CV.</li>
 * <li>Set up an alternate CV read/write procedure and {@code return false} to
 * continue. Don't forget to call
 * {@link #setOptionalCv(boolean flag) setOptionalCv(false)} if the next CV read
 * is not intended to be optional.</li>
 * </ul>
 * </ul>
 * </dd>
 * </dl>
 * <p>
 * TODO:
 * <br>The RailCom&reg; Product ID is a 32 bit unsigned value. {@code productID}
 * is currently {@code int} with -1 signifying a null value. Potential for value
 * conflict exists but changing would involve significant code changes
 * elsewhere.
 *
 * @author Bob Jacobsen Copyright (C) 2001, 2010
 * @author Howard G. Penny Copyright (C) 2005
 * @see jmri.jmrit.symbolicprog.CombinedLocoSelPane
 * @see jmri.jmrit.symbolicprog.NewLocoSelPane
 */
public abstract class IdentifyDecoder extends jmri.jmrit.AbstractIdentify {

    public IdentifyDecoder(jmri.Programmer programmer) {
        super(programmer);
    }

    Manufacturer mfgID = null;  // cv8
    int intMfg = -1;  // needed to hold mfg number in cases that don't match enum
    int modelID = -1; // cv7
    int productIDhigh = -1;
    int productIDlow = -1;
    int productIDhighest = -1;
    int productIDlowest = -1;
    int productID = -1;
    
    /**
     * Represents specific CV8 values.  We don't do
     * product ID for anything not defined here.
    */
    enum Manufacturer {
        DIETZ(115),
        DIY(13),
        DOEHLER(97),
        ESU(151),
        HARMAN(98),
        HORNBY(48),
        QSI(113),
        SOUNDTRAXX(141),
        TCS(153),
        TRAINOMATIC(78),
        ZIMO(145);

        public int value;

        private Manufacturer(int value) {
            this.value = value;
        }
        
        public static Manufacturer forValue(int value) {
            for (var e : values()) {
                if (e.value == value) {
                    return e;
                }
            }
            return null;
        }
    }

    // steps of the identification state machine
    @Override
    public boolean test1() {
        // read cv8
        statusUpdate("Read MFG ID - CV 8");
        readCV("8");
        return false;
    }

    @Override
    public boolean test2(int value) {
        mfgID = Manufacturer.forValue(value);
        intMfg = value;
        statusUpdate("Read MFG version - CV 7");
        readCV("7");
        return false;
    }

    @Override
    public boolean test3(int value) {
        modelID = value;
        if (mfgID == null) return true; // done
        switch (mfgID) {
        case QSI:
            statusUpdate("Set PI for Read Product ID High Byte");
            writeCV("49", 254);
            return false;
        case TCS:
            statusUpdate("Read decoder ID CV 249");
            readCV("249");
            return false;
        case HORNBY:
            if (modelID == 254) { // HN7000
               statusUpdate("Read optional decoder ID CV 200");
                setOptionalCv(true);
                readCV("200");
                return false;
            } else { // other than HN7000
                statusUpdate("Read optional decoder ID CV 159");
                setOptionalCv(true);
                readCV("159");
                return false;
            }
        case ZIMO:
            statusUpdate("Read decoder ID CV 250");
            readCV("250");
            return false;
        case SOUNDTRAXX:
            if (modelID >= 70 && modelID <= 72) {  // SoundTraxx Econami, Tsunami2 and Blunami
                statusUpdate("Read productID high CV253");
                readCV("253");
                return false;
            } else return true;
        case HARMAN:
            statusUpdate("Read decoder ID high CV 112");
            readCV("112");
            return false;
        case ESU:
            if (modelID == 255) {  // ESU recent
                statusUpdate("Set PI for Read productID");
                writeCV("31", 0);
                return false;
            } else return true;
        case DIY:
            statusUpdate("Read decoder product ID #1 CV 47");
            readCV("47");
            return false;
        case DOEHLER:
            statusUpdate("Read optional decoder ID CV 261");
            setOptionalCv(true);
            readCV("261");
            return false;
        case TRAINOMATIC:
            statusUpdate("Read productID #1 CV 510");
            readCV("510");
            return false;
        case DIETZ:
            statusUpdate("Read productID CV 128");
            readCV("128");
            return false;
        default:
            return true;
        }
    }

    @Override
    public boolean test4(int value) {
        switch (mfgID) {
        case QSI:
            statusUpdate("Set SI for Read Product ID High Byte");
            writeCV("50", 4);
            return false;
        case TCS:
            if(value < 129){ //check for mobile decoders
                productID = value;
                return true;
            }
            else{
                productIDlowest = value;
                statusUpdate("Read decoder sound version number");
                readCV("248");
                return false;
            }
        case HORNBY:
            if (modelID == 254) { // HN7000
                productIDlow = value;
                    statusUpdate("Read Product ID High Byte");
                    readCV("200");
                    return false;
            } else { // other than HN7000
               if (isOptionalCv()) {
                    return true;
                }
                if (value == 143) {
                    productIDlow = value;
                    statusUpdate("Read Product ID High Byte");
                    readCV("158");
                    return false;
                } else {
                    productID = value;
                    return true;
                }
            }    
        case ZIMO:
            productID = value;
            return true;
        case SOUNDTRAXX:
            if (modelID >= 70 && modelID <= 72) {  // SoundTraxx Econami, Tsunami2 and Blunami
                productIDhighest = value;
                statusUpdate("Read decoder productID low CV256");
                readCV("256");
                return false;
            } else return true;
        case HARMAN:
            productIDhigh = value;
            statusUpdate("Read decoder ID low CV 113");
            readCV("113");
            return false;
        case ESU:
            statusUpdate("Set SI for Read productID");
            writeCV("32", 255);
            return false;
        case DIY:
            productIDhighest = value;
            statusUpdate("Read decoder product ID #2 CV 48");
            readCV("48");
            return false;
        case DOEHLER:
            if (isOptionalCv()) {
                return true;
            }
            productID = value;
            return true;
        case TRAINOMATIC:
            productIDhigh = value;
            statusUpdate("Read productID #2 CV 509");
            readCV("509");
            return false;
        case DIETZ:
            productID = value;
            return true;
        default:
            log.error("unexpected step 4 reached with value: {}", value);
            return true;
        }
    }

    @Override
    public boolean test5(int value) {
        switch (mfgID) {
        case QSI:
            statusUpdate("Read Product ID High Byte");
            readCV("56");
            return false;
        case HORNBY:
            if (modelID == 254) { // HN7000
                productIDhigh = value;
                productID = (productIDhigh << 8) | productIDlow;
                return true;
            } else { // other than HN7000
                productIDhigh = value;
                productID = (productIDhigh << 8) | productIDlow;
                return true;
            }
        case SOUNDTRAXX:
            if (modelID >= 70 && modelID <= 72) {  // SoundTraxx Econami, Tsunami2 and Blunami
                productIDlow = value;
                readCV("255");
                return false;
            } else return true;
        case HARMAN:
            productIDlow = value;
            productID = (productIDhigh << 8) | productIDlow;
            return true;
        case ESU:
            statusUpdate("Read productID Byte 1");
            readCV("261");
            return false;
        case DIY:
            productIDhigh = value;
            statusUpdate("Read decoder product ID #3 CV 49");
            readCV("49");
            return false;
        case TCS:
            productIDlow = value;
            statusUpdate("Read decoder extended Version ID Low Byte");
            readCV("111");
            return false;
        case TRAINOMATIC:
            productIDlow = value;
            statusUpdate("Read productID #3 CV 508");
            readCV("508");
            return false;
        default:
            log.error("unexpected step 5 reached with value: {}", value);
            return true;
        }
    }

    @Override
    public boolean test6(int value) {
        switch (mfgID) {
        case QSI:
            productIDhigh = value;
            statusUpdate("Set SI for Read Product ID Low Byte");
            writeCV("50", 5);
            return false;
        case ESU:
            productID = value;
            statusUpdate("Read productID Byte 2");
            readCV("262");
            return false;
        case DIY:
            productIDlow = value;
            statusUpdate("Read decoder product ID #4 CV 50");
            readCV("50");
            return false;
        case SOUNDTRAXX:
            if (modelID >= 70 && modelID <= 72) {  // SoundTraxx Econami, Tsunami2 and Blunami
                productIDhigh = value;
                productID = productIDlow | ((productIDhigh & 7) << 8) | (productIDhighest << 11);
                return true;
            } else return true;
        case TCS:
            productIDhigh = value;
            statusUpdate("Read decoder extended Version ID High Byte");
            readCV("110");
            return false;
        case TRAINOMATIC:
            productID = value + (productIDlow * 256) + (productIDhigh * 256 * 256);
            return true;
        default:
            log.error("unexpected step 6 reached with value: {}", value);
            return true;
        }
    }

    @Override
    public boolean test7(int value) {
        switch (mfgID) {
        case QSI:
            statusUpdate("Read Product ID Low Byte");
            readCV("56");
            return false;
        case ESU:
            productID = productID + (value * 256);
            statusUpdate("Read productID Byte 3");
            readCV("263");
            return false;
        case DIY:
            productIDlowest = value;
            productID = (((((productIDhighest << 8) | productIDhigh) << 8) | productIDlow) << 8) | productIDlowest;
            return true;
        case TCS:
            productIDhighest = value;
            if (((productIDlowest >= 129 && productIDlowest <= 135) && (productIDlow == 5))||(modelID >= 5)){
                if ((productIDlowest == 180) && (modelID == 5)) {
                    productID = productIDlowest+(productIDlow*256);
                } else {
                    productID = productIDlowest+(productIDlow*256)+(productIDhigh*256*256)+(productIDhighest*256*256*256);
                }
            } else if ((((productIDlowest >= 129 && productIDlowest <= 135) || (productIDlowest >= 170 && productIDlowest <= 172) || productIDlowest == 180) && (modelID == 4))) {
                productID = productIDlowest+(productIDlow*256);
            } else {
                productID = productIDlowest;
            }
            return true;
        default:
            log.error("unexpected step 7 reached with value: {}", value);
            return true;
        }
    }

    @Override
    public boolean test8(int value) {
        switch (mfgID) {
        case QSI:
            productIDlow = value;
            productID = (productIDhigh * 256) + productIDlow;
            return true;
        case ESU:
            productID = productID + (value * 256 * 256);
            statusUpdate("Read productID Byte 4");
            readCV("264");
            return false;
        default:
            log.error("unexpected step 8 reached with value: {}", value);
            return true;
        }
    }

    @Override
    public boolean test9(int value) {
        if (mfgID == Manufacturer.ESU) {
            productID = productID + (value * 256 * 256 * 256);
            return true;
        }
        log.error("unexpected step 9 reached with value: {}", value);
        return true;
    }

    @Override
    protected void statusUpdate(String s) {
        message(s);
        if (s.equals("Done")) {
            done(intMfg, modelID, productID);
            log.info("Decoder returns mfgID:{};modelID:{};productID:{}", intMfg, modelID, productID);
        } else if (log.isDebugEnabled()) {
            log.debug("received status: {}", s);
        }
    }

    /**
     * Indicate when identification is complete.
     *
     * @param mfgID     identified manufacturer identity
     * @param modelID   identified model identity
     * @param productID identified product identity
     */
    protected abstract void done(int mfgID, int modelID, int productID);

    /**
     * Provide a user-readable message about progress.
     *
     * @param m the message to provide
     */
    protected abstract void message(String m);

    // initialize logging
    private final static Logger log = LoggerFactory.getLogger(IdentifyDecoder.class);

}
