/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.android.test.droidBench;

import java.io.IOException;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;

import soot.jimple.infoflow.InfoflowResults;

@Ignore
public class ImplicitFlowTests extends JUnitTests {
	
	@Test
	public void runTestImplicitFlow1() throws IOException {
		InfoflowResults res = analyzeAPKFile("ImplicitFlows_ImplicitFlow1.apk");
		Assert.assertEquals(2, res.size());
	}

	@Test
	public void runTestImplicitFlow2() throws IOException {
		InfoflowResults res = analyzeAPKFile("ImplicitFlows_ImplicitFlow2.apk");
		Assert.assertEquals(2, res.size());
	}

	@Test
	public void runTestImplicitFlow3() throws IOException {
		InfoflowResults res = analyzeAPKFile("ImplicitFlows_ImplicitFlow3.apk");
		Assert.assertEquals(2, res.size());
	}

	@Test
	public void runTestImplicitFlow4() throws IOException {
		InfoflowResults res = analyzeAPKFile("ImplicitFlows_ImplicitFlow4.apk");
		Assert.assertEquals(2, res.size());
	}

}
