/****************************************************************************
Copyright (c) 2017, Colorado School of Mines and others. All rights reserved.
This program and accompanying materials are made available under the terms of
the Common Public License - v1.0, which accompanies this distribution, and is 
available at http://www.eclipse.org/legal/cpl-v10.html
****************************************************************************/
package sbp;

import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;

import edu.mines.jtk.awt.*;
import edu.mines.jtk.dsp.RecursiveExponentialFilter;
import edu.mines.jtk.io.ArrayInputStream;
import edu.mines.jtk.mosaic.*;
import static edu.mines.jtk.util.ArrayMath.*;

/**
 * Interactive 2D salt boundary picking.
 * @author Xinming Wu, University of Texas at Austin
 * @version 2017.03.28
 */
public class SaltBoundaryPicker2 {

  public SaltBoundaryPicker2(float[][] image) {
    _n1 = image[0].length;
    _n2 = image.length;
    _image = image;
    _picker = new SaltPicker2();
    _env = _picker.applyForInsAmp(image);

    int fontSize = 16;
    int width = _n2;
    int height = _n1;

    // Plot panel.
    PlotPanel.Orientation ppo = PlotPanel.Orientation.X1DOWN_X2RIGHT;
    PlotPanel.AxesPlacement ppap = PlotPanel.AxesPlacement.LEFT_TOP;
    _panel = new PlotPanel(1,1,ppo,ppap);
    _panel.setHLimits(0,0,_n2-1);
    _panel.setVLimits(0,0,_n1-1);
    // Image view.
    _imageView = _panel.addPixels(_image);
    _imageView.setInterpolation(PixelsView.Interpolation.NEAREST);

    // Salt boundary view on top of image view.
    float[][] xs = _picker.getBoundary();
    _boundaryView = _panel.addPoints(xs[0],xs[1]);
    _boundaryView.setLineColor(Color.RED);
    _boundaryView.setLineWidth(3f);

    float[] p1s,p2s;
    int np = _points.size(); 
    if (np==0) {
      p1s = new float[]{-1};
      p2s = new float[]{-1};
    } else {
      p1s = new float[np];
      p2s = new float[np];
      for (int ip=0; ip<np; ++ip) {
        int[] ts = _points.get(ip);
        p1s[ip] = ts[0];
        p2s[ip] = ts[1];
      }
    }
    _pointsView = _panel.addPoints(p1s,p2s);
    _pointsView.setLineStyle(PointsView.Line.NONE);
    _pointsView.setMarkStyle(PointsView.Mark.HOLLOW_CIRCLE);
    _pointsView.setMarkColor(Color.YELLOW);
    _pointsView.setMarkSize(12f);
    _pointsView.setLineWidth(3f);

    // Plot frame.
    _frame = new PlotFrame(_panel);
    _frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    _frame.setFontSize(fontSize);
    _frame.setSize(width,height);
    _frame.setVisible(true);

    makeModesMenusAndToolBar();
  }

  /**
   * Sets the range of values that can be painted.
   * @param vmin the minimum value.
   * @param vmax the maximum value.
   */
  public void setValueRange(double vmin, double vmax) {
    _valueMin = (float)vmin;
    _valueMax = (float)vmax;
    _paintView.setClips(_valueMin,_valueMax);
  }

  ///////////////////////////////////////////////////////////////////////////
  // private


  private int _n1,_n2;
  private float[][] _image;
  private float[][] _env;
  private float _valueMin,_valueMax;
  private SaltPicker2 _picker;

  private PlotPanel _panel;
  private PlotFrame _frame;
  private PixelsView _imageView;
  private PixelsView _paintView;
  private PointsView _boundaryView;
  private PointsView _pointsView;
  private ArrayList<int[]> _points = new ArrayList<int[]>();


  private class PickMode extends Mode {
    public PickMode(ModeManager modeManager) {
      super(modeManager);
      setName("Pick");
      setIcon(loadIcon(PickMode.class,"resources/PickIcon16.png"));
      setMnemonicKey(KeyEvent.VK_P);
      setAcceleratorKey(KeyStroke.getKeyStroke(KeyEvent.VK_P,0));
      setShortDescription("Pick samples");
    }
    protected void setActive(Component component, boolean active) {
      if (component instanceof Tile) {
        if (active) {
          component.addMouseListener(_ml);
        } else {
          component.removeMouseListener(_ml);
        }
      }
    }
    private boolean _down; // true, if mouse is down (painting or erasing)
    private boolean _erasing; // true, if erasing instead of painting
    private int _i1Pick,_i2Pick; // indices of last sample painted
    private Tile _tile; // tile in which painting began
    private MouseListener _ml = new MouseAdapter() {;
      public void mousePressed(MouseEvent e) {
        _erasing = e.isControlDown() || e.isAltDown();
        if (beginPick(e)) {
          _down = true;
          _tile.addMouseMotionListener(_mml);
        }
      }
      public void mouseReleased(MouseEvent e) {
        if (_down) {
          _tile.removeMouseMotionListener(_mml);
          endPick(e);
          _down = false;
        }
      }
    };
    private MouseMotionListener _mml = new MouseMotionAdapter() {
      public void mouseDragged(MouseEvent e) {
        if (_down)
          duringPick(e);
      }
    };
    private int getIndex1(MouseEvent e) {
      _tile = (Tile)e.getSource();
      double x1 = _tile.pixelToWorldVertical(e.getY());
      int i1 = (int)(x1+0.5);
      return (0<=i1 && i1<_n1)?i1:-1;
    }
    private int getIndex2(MouseEvent e) {
      _tile = (Tile)e.getSource();
      double x2 = _tile.pixelToWorldHorizontal(e.getX());
      int i2 = (int)(x2+0.5);
      return (0<=i2 && i2<_n2)?i2:-1;
    }
    private boolean beginPick(MouseEvent e) {
      int i1 = getIndex1(e);
      int i2 = getIndex2(e);
      return pickAt(i1,i2);
    }
    private void duringPick(MouseEvent e) {
      int i1 = getIndex1(e);
      int i2 = getIndex2(e);
      pickAt(i1,i2);
    }
    private void endPick(MouseEvent e) {
      duringPick(e);
      _i1Pick = -1;
      _i2Pick = -1;
    }

    private boolean pickAt(int i1, int i2) {
      if ((i1!=_i1Pick || i2!=_i2Pick) &&
          0<=i1 && i1<_n1 &&
          0<=i2 && i2<_n2) {
        _i1Pick = i1;
        _i2Pick = i2;
        if (_erasing) { // eraser size is 3x3 pixels
          for (int j2=i2-1; j2<=i2+1; ++j2) {
            for (int j1=i1-1; j1<=i1+1; ++j1) {
               if (0<i1 && i1<_n1-1 &&
                   0<i2 && i2<_n2-1) 
                 _points.remove(new int[]{j1,j2});
            }
          }
        } else {
          _points.add(new int[]{i1,i2});
        }
        int np = _points.size();
        float[] i1s = new float[np];
        float[] i2s = new float[np];
        for (int ip=0; ip<np; ++ip) {
          int[] ts = _points.get(ip);
          i1s[ip] = ts[0];
          i2s[ip] = ts[1];
        }
        _pointsView.set(i1s,i2s);
        _picker.initialBoundary(1,i1s,i2s);
        float[][] xs = _picker.getBoundary();
        _boundaryView.set(xs[0],xs[1]);
        return true;
      }
      return false;
    }

  }

  private void makeModesMenusAndToolBar() {

    // Modes.
    ModeManager mm = _frame.getModeManager();
    TileZoomMode tzm = _frame.getTileZoomMode();
    PickMode pm = new PickMode(mm);

    // Menus.
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic('F');
    fileMenu.add(new SaveAsPngAction(_frame)).setMnemonic('a');
    fileMenu.add(new ExitAction()).setMnemonic('x');
    JMenu modeMenu = new JMenu("Mode");
    modeMenu.setMnemonic('M');
    modeMenu.add(new ModeMenuItem(tzm));
    modeMenu.add(new ModeMenuItem(pm));
    JMenu viewMenu = new JMenu("View");
    JMenuBar menuBar = new JMenuBar();
    menuBar.add(fileMenu);
    menuBar.add(modeMenu);
    menuBar.add(viewMenu);
    _frame.setJMenuBar(menuBar);

    // Tool bar.
    JToolBar toolBar = new JToolBar(SwingConstants.VERTICAL);
    toolBar.setRollover(true);
    toolBar.add(new ModeToggleButton(tzm));
    toolBar.add(new ModeToggleButton(pm));
    // clean all
    toolBar.add(new JButton(new AbstractAction("C") {
      public void actionPerformed(ActionEvent e) {
        _points.clear();
        _pointsView.set(new float[]{-1},new float[]{-1});
        _picker.clearBoundary();
        float[][] xs = _picker.getBoundary();
        _boundaryView.set(xs[0],xs[1]);
      }
    }));
    // pick points
    toolBar.add(new JButton(new AbstractAction("P") {
      public void actionPerformed(ActionEvent e) {
        pm.setActive(true);
      }
    }));
    // run the salt boudnary picking
    toolBar.add(new JButton(new AbstractAction("R") {
      public void actionPerformed(ActionEvent e) {
        pm.setActive(false);
        int np = _points.size();
        float[] i1s = new float[np];
        float[] i2s = new float[np];
        for (int ip=0; ip<np; ++ip) {
          int[] ts = _points.get(ip);
          i1s[ip] = ts[0];
          i2s[ip] = ts[1];
        }
        i1s[np-1] = i1s[0];
        i2s[np-1] = i2s[0];
        _picker.initialBoundary(1,i1s,i2s);
        trace("i2b="+i2s[0]);
        trace("i2e="+i2s[np-1]);
        _picker.refine(60,1,10,2,_env);
        float[][] xs = _picker.getBoundary();
        _boundaryView.set(xs[0],xs[1]);
      }
    }));
    _frame.add(toolBar,BorderLayout.WEST);

    // Initially activate paint mode.
  }


  // Actions.
  private class ExitAction extends AbstractAction {
    private ExitAction() {
      super("Exit");
    }
    public void actionPerformed(ActionEvent event) {
      System.exit(0);
    }
  }
  private class SaveAsPngAction extends AbstractAction {
    private PlotFrame _plotFrame;
    private SaveAsPngAction(PlotFrame plotFrame) {
      super("Save as PNG");
      _plotFrame = plotFrame;
    }
    public void actionPerformed(ActionEvent event) {
      JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
      fc.showSaveDialog(_plotFrame);
      File file = fc.getSelectedFile();
      if (file!=null) {
        String filename = file.getAbsolutePath();
        _plotFrame.paintToPng(300,6,filename);
      }
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  // testing

  private static float[][] readImage(int n1, int n2, String fileName) {
    try {
      ArrayInputStream ais = new ArrayInputStream(fileName);
      float[][] x = new float[n2][n1];
      ais.readFloats(x);
      ais.close();
      return x;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static float[][] gain(float sig, float[][] x) {
    float[][] y = mul(x,x);
    RecursiveExponentialFilter ref = new RecursiveExponentialFilter(sig);
    ref.apply1(y,y);
    return div(x,sqrt(y));
  }


  private static void testImagePainterA() {
    int n1 = 850;
    int n2 = 1000;
    float[][] image = 
      readImage(n1,n2,"../../../data/seis/fls/bag/2d/sub2/gx.dat");
    image = gain(50,image);
    float pm = max(abs(image))/3;
    for (int i1=0; i1<n1; ++i1) {
      image[0   ][i1] = pm;
      image[n2-1][i1] = pm;
    }
    SaltBoundaryPicker2 sbp = new SaltBoundaryPicker2(image);
  }

  private static void testImagePainterB() {
    int n1 = 280;
    int n2 = 260;
    float[][] image = readImage(n1,n2,"../../../data/seis/fls/bag/2d/sub1/gx.dat");
    image = gain(50,image);
    SaltBoundaryPicker2 sbp = new SaltBoundaryPicker2(image);
  }

  private static void trace(String s) {
    System.out.println(s);
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        testImagePainterA();
        testImagePainterB();
      }
    });
  }
}
