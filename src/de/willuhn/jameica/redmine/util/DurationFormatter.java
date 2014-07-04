/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.util;

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
    int minutes = ((Number) o).intValue();

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
