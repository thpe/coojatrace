package se.sics.cooja.coojatest

import se.sics.cooja._

import java.util._
import java.io._

import javax.swing._
import java.awt._
import java.awt.event._

import magicsignals._
import operators._
import wrappers._
import interfacewrappers._
import contikiwrappers._
import mspwrappers._



object Conversions {
  import se.sics.cooja.interfaces._

  implicit def simToRichSim(s: Simulation) = new RichSimulation(s)
  implicit def moteToRichMote(m: Mote) = RichMote(m)
  implicit def ledToRichLED(i: LED) = new RichLED(i)
  implicit def radioToRichRadio(r: Radio) = new RichRadio(r)
    
  implicit def radioMediumToRichRadioMedium(rm: RadioMedium) = new RichRadioMedium(rm)
  
  def interface[T <: MoteInterface](i: MoteInterface): T = i.asInstanceOf[T]
  implicit def ledInterface = interface[LED] _
  implicit def radioInterface = interface[Radio] _
}



@ClassDescription("CoojaTest")
@PluginType(PluginType.SIM_PLUGIN)
class CoojaTestPlugin(sim: Simulation, gui: GUI) extends VisPlugin("CoojaTest", gui, false) {
  val logger = org.apache.log4j.Logger.getLogger(this.getClass)
  
  // Aufteilung
  val grid = new GridLayout(0,2)
  setLayout(grid)
  
  // Beschriftung
  val scriptLabel = new JLabel("Script:")

  // Code Textfeld
  val scriptCode = new JTextArea()

  // Ergebnis Textfeld
  val scriptResult = new JTextArea()
  val pwriter = new PrintWriter(new Writer () { 
    def close {}
    def flush {}
    def write(cbuf: Array[Char],  off: Int, len: Int) {
      scriptResult.append(new String(cbuf, off, len));
    }
  })
  scriptResult.setEditable(false)
  
  // Scala Interpreter
  lazy val interpreter = {
    import scala.tools.nsc._
    
    val settings = new Settings()
    
    def jarPathOfClass(className: String) = {
      val resource = className.split('.').mkString("/", "/", ".class")
  	  val path = getClass.getResource(resource).getPath
  	  val indexOfFile = path.indexOf("file:")
  	  val indexOfSeparator = path.lastIndexOf('!')
  	  path.substring(indexOfFile, indexOfSeparator)
    }
    
    val classes = scala.List(
      "org.apache.log4j.Logger", "org.jdom.Element"
    )
    
    val coojaLibs = classes.map(jarPathOfClass(_))
    val dynamicLibs = sim.getGUI.projectDirClassLoader.asInstanceOf[java.net.URLClassLoader].getURLs.map(_.toString.replace("file:", "")).toList
    settings.bootclasspath.value = (coojaLibs ::: dynamicLibs).mkString(":")
    
    new Interpreter(settings, pwriter)
  }
  
  // Flush Button
  val flushButton = new JButton("flush");
  flushButton.addActionListener(new ActionListener() {
    def actionPerformed(e: ActionEvent) {
      interpreter.interpret("logdestination.stream.flush()")
    } 
  })
      
  // Run Button
  val scriptButton = new JButton("run");
  scriptButton.addActionListener(new ActionListener() {
    def actionPerformed(e: ActionEvent) {
      pwriter.flush()
      interpreter.reset()
      Rules.assertions = Nil
      Rules.logrules = Nil
      

      interpreter.interpret("""
        import se.sics.cooja._
        import se.sics.cooja.interfaces._
        import reactive._
        import se.sics.cooja.coojatest.Conversions._
        import se.sics.cooja.coojatest.magicsignals.MagicSignals._
        import se.sics.cooja.coojatest.Rules._
        import se.sics.cooja.coojatest.operators.Operators._

        import se.sics.cooja.coojatest.contikiwrappers._
    	se.sics.cooja.coojatest.contikiwrappers.register()
        import se.sics.cooja.coojatest.mspwrappers._
    	se.sics.cooja.coojatest.mspwrappers.register()
      """)
  
      interpreter.bind("sim", sim.getClass.getName, sim)
      interpreter.bind("pwriter", pwriter.getClass.getName, pwriter)
      
      interpreter.interpret("""
        implicit val theSimulation = sim
        implicit val logdestination = new se.sics.cooja.coojatest.LogDestination(pwriter)
        implicit val observing = new Observing {}
        implicit val dl = new se.sics.cooja.coojatest.magicsignals.DynDepLog
      """)
	
      scriptResult.setText("")
      
      val res = interpreter.interpret(scriptCode.getText())
      logger.debug(res)
      if(res == scala.tools.nsc.InterpreterResults.Success) {
        scriptResult.setForeground(Color.BLACK)
      }
      else {
        scriptResult.setForeground(Color.RED)
      }
        
    }
  })

  //add(scriptLabel)
  add(scriptButton)
  add(flushButton)
  add(new JScrollPane(scriptCode))
  add(new JScrollPane(scriptResult))
  
  setSize(500,500)
  
  // Reload notwendig?
  if(true) { // TODO
    val s1 = "Reload"
    val s2 = "Ignore"
    val options = Array[Object](s1, s2)
    val n = JOptionPane.showOptionDialog(
              GUI.getTopParentContainer(),
              "The CoojaTest plugin was loaded after other plugins. This can load to classloader inconsistencies.\nDo you want to reload the simulation?",
              "Reload simulation?", JOptionPane.YES_NO_OPTION,
              JOptionPane.QUESTION_MESSAGE, null, options, s1);
    if (n == JOptionPane.YES_OPTION) {
      sim.getGUI.reloadCurrentSimulation(false)
    }
  }
  
  override def closePlugin() { 
    logger.debug("closePlugin() called")
  }  
}
