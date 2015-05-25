package vct.col.rewrite;

import vct.col.ast.*;

public class SilverReorder extends AbstractRewriter {

  public SilverReorder(ProgramUnit source) {
    super(source);
  }

  private BlockStatement main_block=null;
  
  @Override
  public void visit(IfStatement s){
    Debug("rewriting if");
    BlockStatement tmp=main_block;
    main_block=currentBlock;
    super.visit(s);
    main_block=tmp;
  }
  
  @Override
  public void visit(DeclarationStatement d){
    super.visit(d);
    if (main_block!=null){
      Warning("moving decl %s",d.name);
      Debug("rewriting if: moving decl");
      main_block.prepend(result);
      result=null;
    }
  }
  
  @Override
  public void visit(BindingExpression e){
    BlockStatement tmp=main_block;
    main_block=null;
    super.visit(e);
    main_block=tmp;
  }
  
}
