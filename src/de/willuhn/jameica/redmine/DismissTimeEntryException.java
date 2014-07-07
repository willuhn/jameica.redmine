/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine;

import de.willuhn.jameica.system.OperationCanceledException;

/**
 * Wird geworfen, wenn eine begonnene Zeiterfassung verworfen wird.
 */
public class DismissTimeEntryException extends OperationCanceledException
{
  /**
   * ct.
   * @param message
   */
  public DismissTimeEntryException(String message)
  {
    super(message);
  }
}


