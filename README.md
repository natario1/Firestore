# Firestore

The lightweight, efficient wrapper for Firestore model data, written in Kotlin, with data-binding and Parcelable support.

```groovy
implementation 'com.otaliastudios:firestore:0.5.0'
kapt 'com.otaliastudios:firestore-compiler:0.5.0'
```

- Efficient and lightweight
- Compiler to avoid reflection
- Built-in Parcelable implementation
- Built-in Data binding support
- Built-in equals & hashcode
- Written in Kotlin using map / list delegation
- Full replacement for `data class` and `Parcelize`

# We know about your schema

The key classes here are `FirestoreDocument` (for documents), `FirestoreMap` and `FirestoreList` (for inner maps and lists).
They use Kotlin delegation so you can declare expected fields using the `by this` syntax:

```kotlin
@FirestoreClass
class User : FirestoreDocument() {
    var type: Int by this
    var imageUrl: String? by this
    var messages: Messages by this
    
    @FirestoreClass
    class Messages : FirestoreList<Message>()
    
    @FirestoreClass
    class Message : FirestoreMap<Any?>() {
        var from: String by this
        var to: String by this
        var text: String? by this
    }
}
```

The compiler will inspect your hierarchy and at runtime, it will know how to instantiate fields
and inner maps or lists, as long as you provide a no arguments constructor for them. This is valid:

```kotlin
val user = User()
val message = Message()
user.messages.add(message) // We didn't have to instantiate Messages()
```

Fiels are instantiated automatically and **lazily**, when requested. 
The map and list implementations parsed by the compiler are also used when retrieving the document
from the network, which makes it much more efficient than using reflection to find setters.

```kotlin
val user: User = documentSnapshot.toFirestoreDocument()
val lastMessage = user.messages.last()
```

## Specify default values

The fields that are marked as not nullable, will be instantiated using their no arguments constructor.
This means that, for example, `Int` defaults to `0`. To specify different defaults, simply use an init block:

```kotlin
@FirestoreClass
class User : FirestoreDocument() {
    var type: Int by this
    
    init {
        type = UserType.ADMIN
    }
}
```

# We cache your documents

Similar to what Firestore-UI does, we keep a static `LruCache` of your documents based on their path.
This means that if you run a query for 50 documents and 20 were already cached, we will reuse their 
instance instead of creating new ones.

```kotlin
val user1: User = documentSnapshot.toFirestoreDocument()
val user2: User = documentSnapshot.toFirestoreDocument()
assert(user1 === user2)
```

Of course, the fields will be updated to reflect the new network data.

# We keep some basic fields

Each object will keep (and save to network) some extra fields that are commonly used, plus have
useful functions to inspect their state with respect to the database.

```kotlin
val isNew: Boolean = user.isNew() // Whether this object was saved to / comes from network
val createdAt: Timestamp? = user.createdAt // When this object was saved to network for the first time. Null if new
val updatedAt: Timestamp? = user.updatedAt // When this object was saved to network for the last time. Null if new
val reference: DocumentReference = user.getReference() // Throws if new
```

We also have built-in reliable implementations for `equals()` and `hashcode()`.

# We know your dirty values

When you update some fields, either some declared field (`user.imageUrl = "url"`) or using the backing
map implementation (`user["imageUrl"] = "url"`), the document will remember that this specific field was changed
with respect to the original values.

Next call to `user.save()` will internally use something like `reference.update(mapOf("imageUrl" to "url"))`,
instead of saving the whole object to the database. This is managed automatically and you don't have to worry about it.

This even works with inner fields and maps!

```kotlin
user.family.father.name = "John"
user.save()

// This will not send the whole User, not the whole Family and not even the whole Father.
// It will call reference.update("user.family.father.name", "John") as it should.
```

# Built-in Data Binding support

The `FirestoreDocument` and `FirestoreMap` classes extend the `BaseObservable` class from the official data binding lib.
Thanks to the compiler, all declared fields will automatically call `notifyPropertyChanged()` for you,
which hugely reduce the work needed to implement databinding and two-way databinding.


In fact, all you have to do is add `@get:Bindable` to your fields:

```kotlin
@FirestoreClass
class Message : FirestoreDocument() {
    @get:Bindable var text: String by this
    @get:Bindable var comment: String by this
}
```

You can now use `message.text` and `message.comment` in XML layouts and they will be updated
when the data model changes.

# We know how to `Parcel`

Documents, maps and lists implement the `Parcelable` interface.

## Local metadata

If your object holds metadata that should not by saved to network (for instance, fields that are not marked with `by this`),
they can be saved and restored to the `Parcel` overriding the class callbacks:

```kotlin
@FirestoreClass
class Message : FirestoreDocument() {
    var text: String by this
    var comment: String by this
    
    var hasBeenRead = false
    
    override fun onWriteToBundle(bundle: Bundle) {
        bundle.putBoolean("hasBeenRead", hasBeenRead)
    }
    
    override fun onReadFromBundle(bundle: Bundle) {
        hasBeenRead = bundle.getBoolean("hasBeenRead")
    }
}
```

## Special types

We offer built in parcelers for `DocumentReference`, `Timestamp` and `FieldValue` types.
If your types do not implement parcelable directly, either have them implement it or register
a parceler using `FirestoreDocument.registerParceler()`:

```kotlin
class App : Application() {

    override fun onCreate() {
        FirestoreDocument.registerParceler(GeoPoint::class, GeoPointParceler())
        FirestoreDocument.registerParceler(Whatever::class, WhateverParceler())
    }
    
    class GeoPointParceler : FirestoreDocument.Parceler<GeoPoint>() {
        // ...
    }
    
    class WhateverParceler : FirestoreDocument.Parceler<Whatever>() {
        // ...
    }
}
```

# Finally, we know how to update 

The document class exposes `delete()`, `save()` and `trySave()` methods. They all return a `Task<?>` object
from the Google gms library, which you should be used to. This lets you add success and failure callbacks,
as well as chain operations with complex dependencies.

## Delete

```kotlin
user.delete().addOnSuccessListener { 
    // User was deleted!               
}.addOnFailureListener {
    // Something went wrong
}
```

The delete operation will throw an exception is the object `isNew()`.

## Save

The `save()` method will check if the object is new or comes from server.

- For a new object, internally this will call `reference.set()` to create your object
- For a backend object, this will call `reference.update()`. As stated above in the dirty fields chapter,
  we only update exactly what was changed programmatically.
  
```kotlin
user.family.father = John()
user.type = User.TYPE_CHILD
user.save().addOnSuccessListener { 
    // User was saved!               
}.addOnFailureListener {
    // Something went wrong
}
```

## Try Save

The `trySave()` method follows an opposite procedure.
It will first try to update the fields you specify to network, and if the call succeeds, it will update
the data document too.
  
```kotlin
user.family.father = null
user.trySave(
    "family.father" to John(),
    "type" to User.TYPE_CHILD
).addOnSuccessListener {
    // User was saved!
    assert(user.family.father is John)
}.addOnFailureListener {
    // Something went wrong.
    assert(user.family.father == null)
}
```


