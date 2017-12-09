Netty是一个异步网络处理框架，在实现中大量使用了Future机制，并在Java自带Future的基础上，增加了Promise机制。这两者的目的都是使异步编程更加方便使用。在阅读源码之前，我们需要对Future的机制有很清楚的认识。

## 4.1 异步编程模型

### 4.1.1 Future

使用Future机制时，我们调用耗时任务会立刻返回一个Future实例，使用该实例能够以阻塞的方式或者在未来某刻获得耗时任务的执行结果，还可以添加监听事件设置后续程序。
```
function Future asynchronousFunction(String arg){
  Future future = new Future(new Callable(){
      public Object call(){
        return null;
      }
  });
  return future;
}
 ReturnHandler handler = asynchronousFunction(); //  耗时函数，但会立即返回一个句柄
 handler.getResult(); // 通过句柄可以等待结果
 handler.addListener(); //通过句柄可以添加完成后执行的事件
 handler.cancel(); // 通过句柄取消耗时任务
```

### 4.1.2 Promise

在Future机制中，业务逻辑所在任务执行的状态（成功或失败）是在Future中实现的，而在Promise中，可以在业务逻辑控制任务的执行结果，相比Future，更加灵活。
```
// 异步的耗时任务接收一个promise
function Promise asynchronousFunction(String arg){
	Promise  promise = new PromiseImpl();
	Object result = null;
    result = search()  //业务逻辑,
    if(success){
         promise.setSuccess(result); // 通知promise当前异步任务成功了，并传入结果
    }else if(failed){
        promise.setFailure(reason); //// 通知promise当前异步任务失败了
     }else if(error){
     	promise.setFailure(error); //// 通知promise当前异步任务发生了异常
      }
}

// 调用异步的耗时任务
Promise promise = asynchronousFunction(promise) ；//会立即返回promise
//添加成功处理/失败处理/异常处理等事件
promise.addListener();// 例如，可以添加成功后执行的事件
doOtherThings() ; //　继续做其他事件，不需要理会asynchronousFunction何时结束
```

在Netty中，Promise继承了Future，包含了这两者的功能。