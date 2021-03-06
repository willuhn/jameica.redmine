/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.ext;

import javax.annotation.Resource;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;

import de.willuhn.jameica.gui.extension.Extendable;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.input.CheckboxInput;
import de.willuhn.jameica.gui.input.SpinnerInput;
import de.willuhn.jameica.gui.input.TextInput;
import de.willuhn.jameica.gui.internal.views.Settings;
import de.willuhn.jameica.gui.parts.InfoPanel;
import de.willuhn.jameica.gui.util.TabGroup;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.SettingsChangedMessage;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.redmine.Plugin;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Erweitert Datei->Einstellungen um die Redmine-Settings.
 */
public class SettingsView implements Extension
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();
  
  @Resource
  private de.willuhn.jameica.redmine.Settings settings;

  private MessageConsumer mc = null;

  private CheckboxInput own        = null;
  private CheckboxInput unassigned = null;
  private TextInput url            = null;
  private TextInput apiKey         = null;
  private SpinnerInput interval    = null;
  
  /**
   * @see de.willuhn.jameica.gui.extension.Extension#extend(de.willuhn.jameica.gui.extension.Extendable)
   */
  public void extend(Extendable extendable)
  {
    if (extendable == null || !(extendable instanceof Settings))
      return;

    this.mc = new MessageConsumer() {

      /**
       * @see de.willuhn.jameica.messaging.MessageConsumer#handleMessage(de.willuhn.jameica.messaging.Message)
       */
      public void handleMessage(Message message) throws Exception
      {
        handleStore();
      }

      /**
       * @see de.willuhn.jameica.messaging.MessageConsumer#getExpectedMessageTypes()
       */
      public Class[] getExpectedMessageTypes()
      {
        return new Class[]{SettingsChangedMessage.class};
      }

      /**
       * @see de.willuhn.jameica.messaging.MessageConsumer#autoRegister()
       */
      public boolean autoRegister()
      {
        return false;
      }
    };
    Application.getMessagingFactory().registerMessageConsumer(this.mc);

    
    Settings settings = (Settings) extendable;
    
    try
    {
      TabGroup tab = new TabGroup(settings.getTabFolder(),i18n.tr("Redmine"));
      
      // Da wir keine echte View sind, haben wir auch kein unbind zum Aufraeumen.
      // Damit wir unsere GUI-Elemente aber trotzdem disposen koennen, registrieren
      // wir einen Dispose-Listener an der Tabgroup
      tab.getComposite().addDisposeListener(new DisposeListener() {
      
        public void widgetDisposed(DisposeEvent e)
        {
          url      = null;
          apiKey   = null;
          interval = null;
          Application.getMessagingFactory().unRegisterMessageConsumer(mc);
        }
      
      });

      tab.addHeadline(i18n.tr("Zugangsdaten des Redmine-Servers"));
      tab.addInput(this.getUrl());
      tab.addInput(this.getApiKey());
      tab.addHeadline(i18n.tr("Verbindungseinstellungen"));
      tab.addInput(this.getInterval());
      tab.addInput(this.getOwn());
      tab.addInput(this.getUnassigned());
      tab.addSeparator();
      
      InfoPanel panel = new InfoPanel();
      panel.setTitle(i18n.tr("Hinweis"));
      panel.setIcon("gtk-info.png");
      panel.setText(i18n.tr("Sie finden Ihren persönlichen API-Zugriffsschlüssel in Redmine unter \"Mein Konto\"\n" +
      		                  "in der rechten Spalte. Klicken Sie unter \"API-Zugriffsschlüssel\" auf \"Anzeigen\"\n" +
      		                  "und kopieren Sie die vollständige Zeichenfolge in das Eingabefeld."));
      tab.addPart(panel);
    }
    catch (ApplicationException ae)
    {
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(), StatusBarMessage.TYPE_ERROR));
    }
    catch (Exception e)
    {
      Logger.error("unable to extend settings",e);
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(i18n.tr("Fehler beim Anzeigen der Redmine-Einstellungen"), StatusBarMessage.TYPE_ERROR));
    }
  }
  
  /**
   * Liefert das Eingabefeld fuer die Server-URL.
   * @return Eingabefeld.
   */
  private TextInput getUrl()
  {
    if (this.url != null)
      return this.url;

    this.url = new TextInput(this.settings.getUrl());
    this.url.setName(i18n.tr("URL des Redmine-Servers"));
    this.url.setHint("https://servername/redmine");
    this.url.setMandatory(true);
    return this.url;
  }
  
  /**
   * Checkbox, zum Konfigurieren, ob nur die eigenen Aufgaben angezeigt werden sollen.
   * @return Checkbox.
   */
  private CheckboxInput getOwn()
  {
    if (this.own != null)
      return this.own;
    
    this.own = new CheckboxInput(this.settings.getOnlyOwnIssues());
    this.own.setName(i18n.tr("Nur Aufgaben anzeigen, die mir zugewiesen sind"));
    return this.own;
  }

  /**
   * Checkbox, zum Konfigurieren, ob Aufgaben angezeigt werden sollen, die niemandem zugewiesen sind.
   * @return Checkbox.
   */
  private CheckboxInput getUnassigned()
  {
    if (this.unassigned != null)
      return this.unassigned;
    
    this.unassigned = new CheckboxInput(this.settings.getUnassignedIssues());
    this.unassigned.setName(i18n.tr("Aufgaben anzeigen, die niemandem zugewiesen sind"));
    return this.unassigned;
  }
  
  /**
   * Liefert das Eingabefeld fuer den API-Key.
   * @return Eingabefeld.
   * @throws ApplicationException
   */
  private TextInput getApiKey() throws ApplicationException
  {
    if (this.apiKey != null)
      return this.apiKey;

    this.apiKey = new TextInput(this.settings.getApiKey());
    this.apiKey.setName(i18n.tr("API-Zugriffsschlüssel"));
    this.apiKey.setMandatory(true);
    return this.apiKey;
  }
  
  private SpinnerInput getInterval() throws ApplicationException
  {
    if (this.interval != null)
      return this.interval;
    
    this.interval = new SpinnerInput(1,60,this.settings.getCacheReloadInterval());
    this.interval.setName(i18n.tr("Zwischengespeicherte Daten neu laden nach jeweils"));
    this.interval.setComment(i18n.tr("Minuten"));
    return this.interval;
  }

  /**
   * Speichert die Einstellungen.
   */
  private void handleStore()
  {
    try
    {
      String url    = (String)  this.getUrl().getValue();
      String apiKey = (String)  this.getApiKey().getValue();
      Integer i     = (Integer) this.getInterval().getValue();
      Boolean b     = (Boolean) this.getOwn().getValue();
      Boolean b2     = (Boolean) this.getUnassigned().getValue();

      this.settings.setUrl(url);
      this.settings.setApiKey(apiKey);
      this.settings.setCacheReloadInterval(i.intValue());
      this.settings.setOnlyOwnIssues(b.booleanValue());
      this.settings.setUnassignedIssues(b2.booleanValue());
    }
    catch (ApplicationException ae)
    {
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(), StatusBarMessage.TYPE_ERROR));
    }
    catch (Exception e)
    {
      Logger.error("unable to save settings",e);
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(i18n.tr("Fehler beim Übernehmen der Einstellungen: {0}",e.getMessage()),StatusBarMessage.TYPE_ERROR));
    }
  }
}


