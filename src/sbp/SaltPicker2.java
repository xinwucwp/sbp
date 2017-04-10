/****************************************************************************
Copyright (c) 2013, Colorado School of Mines and others. All rights reserved.
This program and accompanying materials are made available under the terms of
the Common Public License - v1.0, which accompanies this distribution, and is 
available at http://www.eclipse.org/legal/cpl-v10.html
****************************************************************************/
package sbp;

import java.util.*;
import edu.mines.jtk.dsp.*;
import edu.mines.jtk.util.*;
import edu.mines.jtk.interp.*;
import static edu.mines.jtk.util.ArrayMath.*;

/**
 * 2D salt boundary interpretation with optimal path picking.
 * @author Xinming Wu, Colorado School of Mines
 * @version 2016.16.07
 */


public class SaltPicker2 {

  public void initialBoundary(float d, float[] c1, float[] c2) {
    int nc = c1.length;
    ArrayList<Float> x1a = new ArrayList<Float>();
    ArrayList<Float> x2a = new ArrayList<Float>();
    ArrayList<Float> u1a = new ArrayList<Float>();
    ArrayList<Float> u2a = new ArrayList<Float>();
    for (int ic=1; ic<nc; ++ic) {
      float x1m = c1[ic-1];
      float x1c = c1[ic  ];
      float x2m = c2[ic-1];
      float x2c = c2[ic  ];
      float dx1 = x1c-x1m;
      float dx2 = x2c-x2m;
      float dxc = sqrt(dx1*dx1+dx2*dx2);
      float u1i =  dx2/dxc;
      float u2i = -dx1/dxc;
      u1a.add(u1i); 
      u2a.add(u2i);
      x1a.add(x1m);
      x2a.add(x2m);
      for (float di=d; di<dxc; di+=d) {
        float x1i = x1m+dx1*di/dxc;
        float x2i = x2m+dx2*di/dxc;
        x1a.add(x1i); x2a.add(x2i);
        u1a.add(u1i); u2a.add(u2i);
      }
      if (ic==nc-1) {
        x1a.add(x1c); x2a.add(x2c);
        u1a.add(u1i); u2a.add(u2i);
      }
    }
    int np = x1a.size();
    _xus = new float[4][np];
    for (int ip=0; ip<np; ++ip) {
      _xus[0][ip] = x1a.get(ip);
      _xus[1][ip] = x2a.get(ip);
      _xus[2][ip] = u1a.get(ip);
      _xus[3][ip] = u2a.get(ip);
    }
    if (np>1) smooth(8,_xus);
  }

  public void clearBoundary() {
    _xus = null;
  }

  public float[][] getBoundary() {
    if (_xus==null) {
      float[][] xs = new float[2][2];
      xs[0][0] = -1;
      xs[0][1] = -1;
      xs[1][0] = -1;
      xs[1][1] = -2;
      return xs;
    }
    return new float[][]{_xus[0],_xus[1]};
  }

  public void smooth(float sig, float[] xu) {
    RecursiveExponentialFilter.Edges edges =
      RecursiveExponentialFilter.Edges.OUTPUT_ZERO_SLOPE;
    RecursiveExponentialFilter ref = new RecursiveExponentialFilter(sig);
    ref.setEdges(edges);
    ref.apply1(xu,xu);
  }

  public void smooth(float sig, float[][] xu) {
    int np = xu[0].length;
    RecursiveExponentialFilter.Edges edges =
      RecursiveExponentialFilter.Edges.OUTPUT_ZERO_SLOPE;
    RecursiveExponentialFilter ref = new RecursiveExponentialFilter(sig);
    ref.setEdges(edges);
    ref.apply1(xu,xu);
    for (int ip=0; ip<np; ++ip) {
      float u1i = xu[2][ip];
      float u2i = xu[3][ip];
      float usa = 1f/sqrt(u1i*u1i+u2i*u2i);
      xu[2][ip] = u1i*usa;
      xu[3][ip] = u2i*usa;
    }
  }

  public void regridBoundary(float d, float[] x1, float[] x2) {
    int np = x1.length;
    x1[np-1] = x1[0];
    x2[np-1] = x2[0];
    int k = 0;
    float[] ds = new float[np];
    for (int ip=1; ip<np; ++ip) {
      float x1i = x1[ip];
      float x2i = x2[ip];
      float x1m = x1[ip-1];
      float x2m = x2[ip-1];
      float dx1 = x1i-x1m;
      float dx2 = x2i-x2m;
      float dsi = sqrt(dx1*dx1+dx2*dx2);
      if(dsi>0.0f) {
        k++;
        x1[k] = x1i;
        x2[k] = x2i;
        ds[k] = ds[ip-1]+dsi;
      }
    }
    x1 = copy(k+1,x1);
    x2 = copy(k+1,x2);
    ds = copy(k+1,ds);
    double l = ds[k];
    int n = (int)round(l/d);
    Sampling ss = new Sampling(n,d,0);
    double[] sv = ss.getValues();
    CubicInterpolator cx1 = new CubicInterpolator(ds,x1);
    CubicInterpolator cx2 = new CubicInterpolator(ds,x2);
    float[] x1n = new float[n];
    float[] x2n = new float[n];
    float[] u1n = new float[n];
    float[] u2n = new float[n];
    for (int i=0; i<n; ++i) {
      float si = (float)sv[i];
      x1n[i] = cx1.interpolate(si);
      x2n[i] = cx2.interpolate(si);
    }
    for (int i=0; i<n; ++i) {
      int ip = i+1; if(ip==n) ip=0;
      int im = i-1; if(im<0)  im=n-1;
      float g1 = x1n[ip]-x1n[im];
      float g2 = x2n[ip]-x2n[im];
      float gs = sqrt(g1*g1+g2*g2);
      if(gs>0.0f){g1/=gs;g2/=gs;}
      u1n[i] = -g2;
      u2n[i] =  g1;
    }
    _xus = new float[][]{x1n,x2n,u1n,u2n};
  }


  public void pickNext(
    int r, float d, int w, float a, float[] x1, float[] x2, float[][] fx) {
    smooth(10,x1);
    smooth(10,x2);
    regridBoundary(1f,x1,x2);
    refine(r,d,w,a,fx);
  }

  public float[][] refine(
    int r, float d, int w, float a, float[][] fx) 
  {
    float[][] bs = bandSample(r,d,_xus,fx);
    int m2 = bs.length;
    int m1 = bs[0].length;
    OptimalPathPicker opp = new OptimalPathPicker(w,a);
    float[][] ft = opp.applyTransform(bs);
    float[][] wht = opp.applyForWeight(ft);
    float[][] tms1 = zerofloat(m2,m1);
    float[][] tms2 = zerofloat(m2,m1);
    float[] pik1 = opp.forwardPick(r,wht,tms1);
    float[] pik2 = opp.backwardPick(round(pik1[m2-1]),wht,tms2);
    int np = _xus[0].length;
    for (int ip=0; ip<np; ++ip) {
      float u1i = _xus[2][ip];
      float u2i = _xus[3][ip];
      _xus[0][ip] += u1i*(pik2[ip]-r)*d;
      _xus[1][ip] += u2i*(pik2[ip]-r)*d;
    }
    _xus[0][np-1] = _xus[0][0];
    _xus[1][np-1] = _xus[1][0];
    return bs;
  }

  public float[][] bandSample(
    int r, float d, float[][] xu, float[][] fx) {
    int n2 = fx.length;
    int n1 = fx[0].length;
    int np = xu[0].length;
    Sampling s1 = new Sampling(n1);
    Sampling s2 = new Sampling(n2);
    float[][] fbs = new float[np][2*r+1];
    SincInterpolator si = new SincInterpolator();
    si.setExtrapolation(SincInterpolator.Extrapolation.CONSTANT);
    float sig=50f;
    float pi = (float)Math.PI;
    float sigs = sig*sig;
    float gaus = 1f/sqrt(2f*sigs*pi);
    for (int ip=0; ip<np; ++ip) {
      float x1i = xu[0][ip];
      float x2i = xu[1][ip];
      float u1i = xu[2][ip];
      float u2i = xu[3][ip];
      fbs[ip][r] = si.interpolate(s1,s2,fx,x1i,x2i)*gaus;
      for (int ir=1; ir<=r; ++ir) {
        float ird = ir*d;
        float y1i = x1i+u1i*ird;
        float y2i = x2i+u2i*ird;
        float fxi = si.interpolate(s1,s2,fx,y1i,y2i);
        float gui = exp(-0.5f*ird*ird/sigs);
        fbs[ip][ir+r]=fxi*gui*gaus;
      }
      for (int ir=-r; ir<=-1; ++ir) {
        float ird = ir*d;
        float y1i = x1i+u1i*ird;
        float y2i = x2i+u2i*ird;
        float fxi = si.interpolate(s1,s2,fx,y1i,y2i);
        float gui = exp(-0.5f*ird*ird/sigs);
        fbs[ip][ir+r]=fxi*gui*gaus;
      }
    }
    sub(fbs,min(fbs),fbs);
    div(fbs,max(fbs),fbs);
    return fbs;
  }

  public void combineEnvAndSaltLike(
    float pmin, float[][][] p2, float[][][] p3, float[][][] pa, float[][][] sl) 
  {
    sub(pa,min(pa),pa);
    div(pa,max(pa),pa);
    sub(sl,min(sl),sl);
    div(sl,max(sl),sl);
    int n3 = pa.length;
    int n2 = pa[0].length; 
    int n1 = pa[0][0].length; 
    for (int i3=0; i3<n3; ++i3) {
    for (int i2=0; i2<n2; ++i2) {
    for (int i1=0; i1<n1; ++i1) {
      float p2i = abs(p2[i3][i2][i1]);
      float p3i = abs(p3[i3][i2][i1]);
      if(p2i<pmin&&p3i<pmin) {
        sl[i3][i2][i1] = pa[i3][i2][i1];
      }
    }}}
    float slm = max(sl)*0.5f;
    for (int i3=0; i3<n3; ++i3) {
    for (int i1=0; i1<n1; ++i1) {
      sl[i3][0   ][i1] = slm;
      sl[i3][n2-1][i1] = slm;
    }}
  }

  public float[][][] applyForInsAmp(final float[][][] fx) {
    final int n3 = fx.length;
    final int n2 = fx[0].length; 
    final int n1 = fx[0][0].length; 
    final float[][][] pa = new float[n3][n2][n1];
    final HilbertTransformFilter hbt = new HilbertTransformFilter();
    Parallel.loop(n3,new Parallel.LoopInt() {
      public void compute(int i3) {
      float[][] fx3 = fx[i3];
      float[][] pa3 = pa[i3];
      for (int i2=0; i2<n2; i2++){
      float[] fi = new float[n1];
      hbt.apply(n1,fx3[i2],fi);
      for (int i1=0; i1<n1; i1++){
        float fxi = fi[i1];
        float fxr = fx3[i2][i1];
        float pai = sqrt(fxr*fxr+fxi*fxi);
        if(Float.isInfinite(pai)||Float.isNaN(pai)){
          pa3[i2][i1] = 0f;
        } else { pa3[i2][i1] = pai; }
      }}
    }}); 
    return pa;
  }


  public float[][] applyForInsAmp(final float[][] fx) {
    final int n2 = fx.length;
    final int n1 = fx[0].length; 
    final float[][] pa = new float[n2][n1];
    final HilbertTransformFilter hbt = new HilbertTransformFilter();
    Parallel.loop(n2,new Parallel.LoopInt() {
      public void compute(int i2) {
      float[] fi = new float[n1];
      hbt.apply(n1,fx[i2],fi);
      for (int i1=0; i1<n1; i1++){
        float fxi = fi[i1];
        float fxr = fx[i2][i1];
        float pai = sqrt(fxr*fxr+fxi*fxi);
        if(Float.isInfinite(pai)||Float.isNaN(pai)){
          pa[i2][i1] = 0f;
        } else { pa[i2][i1] = pai; }
      }
    }}); 
    return pa;
  }

  private float[][] _xus=null;

}
