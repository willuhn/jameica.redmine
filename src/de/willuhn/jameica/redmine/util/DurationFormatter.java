/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.util;

import com.taskadapter.redmineapi.bean.TimeEntry;

import de.willuhn.jameica.gui.formatter.Formatter;

/**
 * Formatter fuer Zeitaufwaende.
 */
public class DurationFormatter implements Formatter
{
  /**
   * @see de.willuhn.jameica.gui.formatter.Formatter#format(java.lang.Object)
   */
  public String format(Object o)
  {
    if (o == null)
      return null;

    if (o instanceof Number)
    {
      return this.formatMinutes(((Number) o).intValue());
    }
    
    if (o instanceof TimeEntry)
    {
      long now = System.currentTimeMillis();
      long started = ((TimeEntry) o).getCreatedOn().getTime();
      int minutes = (int) (now - started) / 1000 / 60;
      return this.formatMinutes(minutes);
    }
    
    return null;
  }
  
  /**
   * Liefert die Minuten in formatierter Form zurueck.
   * @param minutes die Minuten.
   * @return formatierte Form als HH:mm.
   */
  private String formatMinutes(int minutes)
  {
    // Stunden ermitteln
    int hours = minutes / 60;
    
    // restliche Minuten ermitteln
    minutes -= hours*60;

    String delim = ":";
    if (minutes < 10)
      delim = ":0";
    return hours + delim + minutes + " h";
  }
}
