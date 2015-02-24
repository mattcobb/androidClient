package net.inbetween;


public abstract class Action
{
   protected ActionType type;

   public ActionType getType()
   {
      return type;
   }

   public void setType(ActionType type)
   {
      this.type = type;
   }
}
