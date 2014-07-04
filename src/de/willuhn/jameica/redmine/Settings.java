/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine;

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.security.Wallet;
import de.willuhn.jameica.security.crypto.AESEngine;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Bean fuer die Settings.
 */
@Lifecycle(Type.CONTEXT)
public class Settings
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();
  
  private Wallet wallet = null;
  private de.willuhn.jameica.system.Settings settings = null;
  
  /**
   * Liefert die Server-URL der Redmine-Installation.
   * @return die Server-URL der Redmine-Installation.
   */
  public String getUrl()
  {
    return this.getSettings().getString("redmine.url",null);
  }
  
  /**
   * Speichert die Server-URL der Redmine-Installation.
   * @param url die Server-URL der Redmine-Installation.
   */
  public void setUrl(String url)
  {
    this.getSettings().setAttribute("redmine.url",url);
  }
  
  /**
   * Liefert den API-Key der Redmine-Installation.
   * @return der API-Key der Redmine-Installation.
   * @throws ApplicationException
   */
  public String getApiKey() throws ApplicationException
  {
    return (String) this.getWallet().get("redmine.apikey");
  }
  
  /**
   * Speichert den API-Key der Redmine-Installation.
   * @param apiKey der API-Key der Redmine-Installation.
   * @throws ApplicationException
   */
  public void setApiKey(String apiKey) throws ApplicationException
  {
    try
    {
      this.getWallet().set("redmine.apikey",apiKey);
    }
    catch (ApplicationException ae)
    {
      throw ae;
    }
    catch (Exception e)
    {
      Logger.error("unable to save wallet",e);
      throw new ApplicationException(i18n.tr("Wallet kann nicht gespeichert werden: {0}",e.getMessage()),e);
    }
  }
  
  /**
   * Liefert das Intervall in Minuten, nach denen der Cache neu geladen wird.
   * @return das Intervall in Minuten, nach denen der Cache neu geladen wird.
   */
  public int getCacheReloadInterval()
  {
    return this.getSettings().getInt("cache.reload.minutes",5);
  }
  
  /**
   * Speichert das Intervall in Minuten, nach denen der Cache neu geladen wird.
   * @param minutes das Intervall in Minuten, nach denen der Cache neu geladen wird.
   * @throws ApplicationException
   */
  public void setCacheReloadInterval(int minutes) throws ApplicationException
  {
    if (minutes < 1)
      throw new ApplicationException(i18n.tr("Ungültiges Reload-Intervall."));
    this.getSettings().setAttribute("cache.reload.minutes",minutes);
  }
  
  /**
   * Liefert den Settings-Container.
   * @return der Settings-Container.
   */
  private synchronized de.willuhn.jameica.system.Settings getSettings()
  {
    if (this.settings == null)
      this.settings = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getSettings();
    
    return this.settings;
  }
  
  /**
   * Liefert das Wallet zum verschluesselten Speichern von Einstellungen. 
   * @return das Wallet.
   * @throws ApplicationException
   */
  private synchronized Wallet getWallet() throws ApplicationException
  {
    if (this.wallet == null)
    {
      try
      {
        this.wallet = new Wallet(Plugin.class,new AESEngine());
      }
      catch (ApplicationException ae)
      {
        throw ae;
      }
      catch (Exception e)
      {
        Logger.error("unable to open wallet",e);
        throw new ApplicationException(i18n.tr("Wallet kann nicht geöffnet werden: {0}",e.getMessage()),e);
      }
    }
    
    return this.wallet;
  }
}


