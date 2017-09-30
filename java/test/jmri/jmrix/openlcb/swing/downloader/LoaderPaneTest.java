package jmri.jmrix.openlcb.swing.downloader;

import jmri.util.JUnitUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Paul Bender Copyright (C) 2017	
 */
public class LoaderPaneTest extends jmri.util.swing.JmriPanelTest {

    // The minimal setup for log4J
    @Before
    @Override
    public void setUp() {
        JUnitUtil.setUp();
        panel = new LoaderPane();
    }

    @After
    @Override
    public void tearDown() {
        JUnitUtil.tearDown();
    }

    // private final static Logger log = LoggerFactory.getLogger(LoaderPaneTest.class);

}
