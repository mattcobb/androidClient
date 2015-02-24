package net.inbetween.actions;

import net.inbetween.Action;
import net.inbetween.ActionType;

public class PhoneHomeAction extends Action
{
   public PhoneHomeAction()
   {
      super();
      type = ActionType.ACTION_PHONE_HOME;
   }
}
