package jmri.jmrix.marklin.swing.monitor;

import jmri.jmrix.marklin.MarklinConstants;
import jmri.jmrix.marklin.MarklinReply;

/**
 * Class to convert Marklin Can bus messages to a human readable form
 */
public class MarklinMon {

    // class only supplies static methods
    private MarklinMon(){}

    public static String displayReply(MarklinReply r) {

        StringBuilder sb = new StringBuilder();
        appendPriority(r, sb);
        appendCommand(r, sb);
        appendResponse(r, sb);
        appendAddress(r, sb);
        appendCanHex(r, sb);
        return sb.toString();
    }

    private static void appendPriority(MarklinReply r, StringBuilder sb) {
        sb.append("Priority ");
        switch (r.getPriority()) {
            case MarklinConstants.PRIO_1:
                sb.append("1, Stop/Go/Short");
                break;
            case MarklinConstants.PRIO_2:
                sb.append("2, Feedback");
                break;
            case MarklinConstants.PRIO_3:
                sb.append("3, Engine Stop");
                break;
            case MarklinConstants.PRIO_4:
                sb.append("4, Engine/accessory command");
                break;
            default:
                sb.append(Bundle.getMessage("StateUnknown"));
        }
    }

    private static void appendCommand(MarklinReply r, StringBuilder sb) {
        sb.append(" Command: ");
        int command = r.getCommand();
        if (command == MarklinConstants.SYSCOMMANDSTART) {
            sb.append("System");
        } else if (command >= MarklinConstants.MANCOMMANDSTART && command <= MarklinConstants.MANCOMMANDEND) {
            switch (r.getCommand()) {
                case MarklinConstants.LOCODIRECTION:
                    sb.append("Change of direction ").append(r.getElement(9));
                    break;
                case MarklinConstants.LOCOSPEED:
                    sb.append("Change of speed ").append((r.getElement(9) & 0xff << 8) + (r.getElement(10) & 0xff));
                    break;
                case MarklinConstants.LOCOFUNCTION:
                    sb.append("Function: ").append(r.getElement(9)).append(" state: ").append(r.getElement(10));
                    break;
                default:
                    sb.append("Management");
            }
        } else if (command >= MarklinConstants.ACCCOMMANDSTART && command <= MarklinConstants.ACCCOMMANDEND) {
            sb.append("Accessory");
            switch (r.getElement(9)) {
                case 0x00:
                    sb.append(Bundle.getMessage("SetTurnoutState", Bundle.getMessage("TurnoutStateThrown")));
                    break;
                case 0x01:
                    sb.append(Bundle.getMessage("SetTurnoutState", Bundle.getMessage("TurnoutStateClosed")));
                    break;
                default:
                    sb.append("Unknown state command ").append(r.getElement(9));
            }
        } else if (command >= MarklinConstants.SOFCOMMANDSTART && command <= MarklinConstants.SOFCOMMANDEND) {
            sb.append("Software");
        } else if (command >= MarklinConstants.GUICOMMANDSTART && command <= MarklinConstants.GUICOMMANDEND) {
            sb.append("GUI");
        } else if (command >= MarklinConstants.AUTCOMMANDSTART && command <= MarklinConstants.AUTCOMMANDEND) {
            sb.append("Automation");
        } else if (command >= MarklinConstants.FEECOMMANDSTART && command <= MarklinConstants.FEECOMMANDEND) {
            sb.append("Feedback");
        }
    }

    private static void appendResponse(MarklinReply r, StringBuilder sb) {
        if (r.isResponse()) {
            sb.append(" ").append( Bundle.getMessage("ReplyMessage"));
        } else {
            sb.append(" ").append( Bundle.getMessage("RequestMessage"));
        }
    }

    private static void appendAddress(MarklinReply r, StringBuilder sb) {
        long addr = r.getAddress();
        if (addr >= MarklinConstants.MM1START && addr <= MarklinConstants.MM1END) {
            if (addr == 0) {
                sb.append(" Broadcast");
            } else {
                sb.append(" ").append( Bundle.getMessage("MonTrafToLocoAddress", addr));
            }
        } else if (addr >= MarklinConstants.MM1FUNCTSTART && addr <= MarklinConstants.MM1FUNCTEND) {
            addr -= MarklinConstants.MM1FUNCTSTART;
            sb.append(" to MM Function decoder ").append(addr);
        } else if (addr >= MarklinConstants.MM1LOCOSTART && addr <= MarklinConstants.MM1LOCOEND) {
            addr -= MarklinConstants.MM1LOCOSTART;
            sb.append(" ").append( Bundle.getMessage("MonTrafToLocoAddress", addr));
        } else if (addr >= MarklinConstants.SX1START && addr <= MarklinConstants.SX1END) {
            addr -= MarklinConstants.SX1START;
            sb.append(" to SX Address ").append( addr);
        } else if (addr >= MarklinConstants.SX1ACCSTART && addr <= MarklinConstants.SX1ACCEND) {
            addr -= MarklinConstants.SX1ACCSTART;
            sb.append(" to SX Accessory Address ").append(addr);
        } else if (addr >= MarklinConstants.MM1ACCSTART && addr <= MarklinConstants.MM1ACCEND) {
            addr -= MarklinConstants.MM1ACCSTART;
            sb.append(" to MM Accessory Address ").append( addr);
        } else if (addr >= MarklinConstants.DCCACCSTART && addr <= MarklinConstants.DCCACCEND) {
            addr -= MarklinConstants.DCCACCSTART;
            sb.append(" to DCC Accessory Address ").append( addr);
        } else if (addr >= MarklinConstants.MFXSTART && addr <= MarklinConstants.MFXEND) {
            addr -= MarklinConstants.MFXSTART;
            sb.append(" to MFX Address ").append( addr);
        } else if (addr >= MarklinConstants.SX2START && addr <= MarklinConstants.SX2END) {
            addr -= MarklinConstants.SX2START;
            sb.append(" to SX2 Address ").append( addr);
        } else if (addr >= MarklinConstants.DCCSTART && addr <= MarklinConstants.DCCEND) {
            addr -= MarklinConstants.DCCSTART;
            sb.append(" to DCC Address ").append( addr);
        }
    }

    private static void appendCanHex(MarklinReply r, StringBuilder sb) {
        sb.append("0x").append( Integer.toHexString(r.getCanData()[0]));
        for (int i = 1; i < r.getCanData().length; i++) {
            sb.append(", 0x").append(Integer.toHexString(r.getCanData()[i]));
        }
    }

}
