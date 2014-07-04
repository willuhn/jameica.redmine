/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.messaging;

import javax.annotation.Resource;

import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.SystemMessage;
import de.willuhn.jameica.redmine.gui.SysTray;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

/**
 * Wird aufgerufen, wenn Jameica gestartet ist und laedt das Systray-Icon.
 */
public class SysTrayMessageConsumer implements MessageConsumer
{
  @Resource
  private SysTray systray;
  
  /**
   * @see de.willuhn.jameica.messaging.MessageConsumer#getExpectedMessageTypes()
   */
  @Override
  public Class[] getExpectedMessageTypes()
  {
    return new Class[]{SystemMessage.class};
  }

  /**
   * @see de.willuhn.jameica.messaging.MessageConsumer#handleMessage(de.willuhn.jameica.messaging.Message)
   */
  public void handleMessage(Message message) throws Exception
  {
    if (Application.inServerMode())
    {
      Logger.info("running in server mode, skipping systray icon");
      return;
    }
    
    final SystemMessage msg = (SystemMessage) message;
    
    GUI.getDisplay().syncExec(new Runnable() {
      @Override
      public void run()
      {
        int status = msg.getStatusCode();
        
        if (status == SystemMessage.SYSTEM_STARTED)
          systray.start();
        else
          systray.stop();
      }
    });
  }

  /**
   * @see de.willuhn.jameica.messaging.MessageConsumer#autoRegister()
   */
  @Override
  public boolean autoRegister()
  {
    return true;
  }

}


