/*******************************************************************************
 * This file is part of the Virgo Web Server.
 *
 * Copyright (c) 2011 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SpringSource, a division of VMware - initial API and implementation and/or initial documentation
 *******************************************************************************/

package org.eclipse.equinox.internal.region;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Set;
import org.easymock.EasyMock;
import org.eclipse.equinox.region.*;
import org.eclipse.equinox.region.RegionDigraph.FilteredRegion;
import org.eclipse.virgo.teststubs.osgi.framework.StubBundle;
import org.eclipse.virgo.teststubs.osgi.framework.StubBundleContext;
import org.junit.*;
import org.osgi.framework.*;

public class StandardRegionDigraphTests {

	private RegionDigraph digraph;

	private Region mockRegion1;

	private Region mockRegion2;

	private Region mockRegion3;

	private RegionFilter regionFilter1;

	private RegionFilter regionFilter2;

	private Bundle mockBundle;

	@Before
	public void setUp() throws Exception {
		StubBundle stubSystemBundle = new StubBundle(0L, "osgi.framework", new Version("0"), "loc");
		StubBundleContext stubBundleContext = new StubBundleContext();
		stubBundleContext.addInstalledBundle(stubSystemBundle);
		this.digraph = new StandardRegionDigraph(stubBundleContext, new ThreadLocal<Region>());

		this.mockRegion1 = EasyMock.createMock(Region.class);
		EasyMock.expect(this.mockRegion1.getName()).andReturn("mockRegion1").anyTimes();

		this.mockRegion2 = EasyMock.createMock(Region.class);
		EasyMock.expect(this.mockRegion2.getName()).andReturn("mockRegion2").anyTimes();

		this.mockRegion3 = EasyMock.createMock(Region.class);
		EasyMock.expect(this.mockRegion3.getName()).andReturn("mockRegion3").anyTimes();

		this.mockBundle = EasyMock.createMock(Bundle.class);
	}

	private void setDefaultFilters() throws InvalidSyntaxException {
		this.regionFilter1 = digraph.createRegionFilterBuilder().build();
		this.regionFilter2 = digraph.createRegionFilterBuilder().build();
	}

	private void setAllowedFilters(String b1Name, Version b1Version, String b2Name, Version b2Version) throws InvalidSyntaxException {
		String filter1 = "(&(" + RegionFilter.VISIBLE_BUNDLE_NAMESPACE + "=" + b1Name + ")(" + Constants.BUNDLE_VERSION_ATTRIBUTE + "=" + b1Version + "))";
		regionFilter1 = digraph.createRegionFilterBuilder().allow(RegionFilter.VISIBLE_BUNDLE_NAMESPACE, filter1).build();

		String filter2 = "(&(" + RegionFilter.VISIBLE_BUNDLE_NAMESPACE + "=" + b2Name + ")(" + Constants.BUNDLE_VERSION_ATTRIBUTE + "=" + b2Version + "))";
		regionFilter2 = digraph.createRegionFilterBuilder().allow(RegionFilter.VISIBLE_BUNDLE_NAMESPACE, filter2).build();

	}

	private void replayMocks() {
		EasyMock.replay(this.mockRegion1, this.mockRegion2, this.mockRegion3, this.mockBundle);
	}

	@After
	public void tearDown() throws Exception {
		EasyMock.verify(this.mockRegion1, this.mockRegion2, this.mockRegion3, this.mockBundle);
	}

	@Test
	public void testConnect() throws BundleException, InvalidSyntaxException {
		setDefaultFilters();
		replayMocks();

		this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
	}

	@Test
	public void testConnectWithFilterContents() throws BundleException, InvalidSyntaxException {
		String b1Name = "b1";
		Version b1Version = new Version("0");
		String b2Name = "b2";
		Version b2Version = new Version("0");
		setAllowedFilters(b1Name, b1Version, b2Name, b2Version);
		EasyMock.expect(this.mockRegion1.getBundle(b1Name, b1Version)).andReturn(null).anyTimes();
		EasyMock.expect(this.mockRegion1.getBundle(b2Name, b2Version)).andReturn(null).anyTimes();

		replayMocks();

		this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
		this.digraph.connect(this.mockRegion1, this.regionFilter2, this.mockRegion3);
	}

	@Test(expected = BundleException.class)
	public void testConnectLoop() throws BundleException, InvalidSyntaxException {
		setDefaultFilters();
		replayMocks();

		this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion1);
	}

	@Test(expected = BundleException.class)
	public void testDuplicateConnection() throws BundleException, InvalidSyntaxException {
		setDefaultFilters();
		replayMocks();

		this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
		this.digraph.connect(this.mockRegion1, this.regionFilter2, this.mockRegion2);
	}

	@Test
	public void testGetEdges() throws BundleException, InvalidSyntaxException {
		setDefaultFilters();
		replayMocks();

		this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
		this.digraph.connect(this.mockRegion1, this.regionFilter2, this.mockRegion3);
		this.digraph.connect(this.mockRegion2, this.regionFilter2, this.mockRegion1);

		Set<FilteredRegion> edges = this.digraph.getEdges(this.mockRegion1);

		assertEquals(2, edges.size());

		for (FilteredRegion edge : edges) {
			if (edge.getRegion().equals(this.mockRegion2)) {
				assertEquals(this.regionFilter1, edge.getFilter());
			} else if (edge.getRegion().equals(this.mockRegion3)) {
				assertEquals(this.regionFilter2, edge.getFilter());
			} else {
				fail("unexpected edge");
			}
		}
	}

	@Test
	public void testRemoveRegion() throws BundleException, InvalidSyntaxException {
		setDefaultFilters();
		replayMocks();

		this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
		this.digraph.connect(this.mockRegion2, this.regionFilter2, this.mockRegion1);
		assertNotNull(this.digraph.getRegion("mockRegion1"));
		assertNotNull(this.digraph.getRegion("mockRegion2"));
		this.digraph.removeRegion(this.mockRegion1);
		assertNull(this.digraph.getRegion("mockRegion1"));
		assertNotNull(this.digraph.getRegion("mockRegion2"));
	}

	@Test
	public void testGetRegions() throws BundleException, InvalidSyntaxException {
		setDefaultFilters();
		replayMocks();

		this.digraph.connect(this.mockRegion1, this.regionFilter1, this.mockRegion2);
		Set<Region> regions = this.digraph.getRegions();
		assertEquals(2, regions.size());
		assertTrue(regions.contains(this.mockRegion1));
		assertTrue(regions.contains(this.mockRegion2));
	}

	private static final String REGION_A = "A";
	private static final String REGION_B = "B";
	private static final String REGION_C = "C";
	private static final String REGION_D = "D";

	@Test
	public void testCopyRegion() throws BundleException, InvalidSyntaxException {
		replayMocks(); // needed to allow teardown to succeed.
		RegionDigraph testDigraph = new StandardRegionDigraph();
		long bundleId = 1;
		Region a = testDigraph.createRegion(REGION_A);
		a.addBundle(bundleId++);
		a.addBundle(bundleId++);
		Region b = testDigraph.createRegion(REGION_B);
		b.addBundle(bundleId++);
		b.addBundle(bundleId++);
		Region c = testDigraph.createRegion(REGION_C);
		c.addBundle(bundleId++);
		c.addBundle(bundleId++);
		Region d = testDigraph.createRegion(REGION_D);
		d.addBundle(bundleId++);
		d.addBundle(bundleId++);

		testDigraph.connect(a, testDigraph.createRegionFilterBuilder().allow("a", "(a=x)").build(), b);
		testDigraph.connect(b, testDigraph.createRegionFilterBuilder().allow("b", "(b=x)").build(), c);
		testDigraph.connect(c, testDigraph.createRegionFilterBuilder().allow("c", "(c=x)").build(), d);
		testDigraph.connect(d, testDigraph.createRegionFilterBuilder().allow("d", "(d=x)").build(), a);
		RegionDigraph testCopy = testDigraph.copy();
		StandardRegionDigraphPeristenceTests.assertEquals(testDigraph, testCopy);

		a = testCopy.getRegion(REGION_A);
		b = testCopy.getRegion(REGION_B);
		c = testCopy.getRegion(REGION_C);
		d = testCopy.getRegion(REGION_D);

		for (Region region : testCopy) {
			testCopy.removeRegion(region);
		}

		testCopy.connect(a, testCopy.createRegionFilterBuilder().allow("a", "(a=x)").build(), d);
		testCopy.connect(b, testCopy.createRegionFilterBuilder().allow("b", "(b=x)").build(), a);
		testCopy.connect(c, testCopy.createRegionFilterBuilder().allow("c", "(c=x)").build(), b);
		testCopy.connect(d, testCopy.createRegionFilterBuilder().allow("d", "(d=x)").build(), c);

		try {
			StandardRegionDigraphPeristenceTests.assertEquals(testDigraph, testCopy);
			fail("Digraphs must not be equal");
		} catch (AssertionError e) {
			// expected
		}
		testDigraph.replace(testCopy);
		StandardRegionDigraphPeristenceTests.assertEquals(testDigraph, testCopy);
	}
}
