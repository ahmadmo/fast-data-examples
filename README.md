# Fast Data Examples

### *The goal of this repository is to demo the functionality of a variety of tools and technologies in the field of Big/Fast Data and integrate them in some RealWorld examples.*

## Why Big Data?
> Big data is like teenage sex: everyone talks about it, nobody really knows how to do it, everyone thinks everyone else is doing it, so everyone claims they are doing it...

This is a famous quote and you probably already heard it.

*Data, in today’s business and technology world, is indispensable. The field of Big Data and Big Data Analytics is growing day by day. Most software developers think it's tough to learn big data and tend to be scared of it. Learning big data CAN be easy and enjoyable if you learn it by examples, but, of course, it is so difficult to master it :D*

Here, we aim to learn and possibly master Big/Fast Data by some RealWorld examples provided by the open source community.

## Why Fast Data?
> ### Introduction
> Until recently, big data systems have been batch oriented, where data is captured in distributed filesystems or databases and then processed in batches or studied interactively, as in data warehousing scenarios. Now, it is a competitive disadvantage to rely exclusively on batch-mode processing, where data arrives without immediate extraction of valuable information.
>
> Hence, big data systems are evolving to be more stream oriented, where data is processed as it arrives, leading to so-called fast data systems that ingest and process continuous, potentially infinite data streams.
>
> Ideally, such systems still support batch-mode and interactive processing, because traditional uses, such as data warehousing, haven’t gone away. In many cases, we can rework batch-mode analytics to use the same streaming infrastructure, where we treat our batch data sets as finite streams.
> ### Streaming Architecture
> Because there are so many streaming systems and ways of doing streaming, and because everything is evolving quickly, we have to narrow our focus to a representative sample of current systems and a reference architecture that covers the essential features.
>
> ![Figure 2-1](https://raw.githubusercontent.com/ahmadmo/fast-data-examples/master/fast-data-architecture.png)

([Fast Data Architectures For Streaming Applications - 2nd Edition](https://www.lightbend.com/blog/new-edition-fast-data-architectures-for-streaming-applications-2nd-ed))

## Tools and Technologies
There are tons of tools and technologies in the field of Big/Fast Data, but some of them are more mature and widely used in *real* applications.  
In this repository we prefer to provide exmaples with the tools and technologies demonstrated in the [Fast Data Architecture](#streaming-architecture).

## Programming Languages
As described in the [previous section](#tools-and-technologies), any supported programming language by the tools and technologies demonstrated in the [Fast Data Architecture](#streaming-architecture) can be used in this repository.

Most used programming languages are:
- **Java**
- **Scala**
- **Kotlin**

## Code Style
For [**Scala**](https://docs.scala-lang.org/style/) and [**Kotlin**](https://kotlinlang.org/docs/reference/coding-conventions.html) please use their official code style. For other programming languages such as **Java** and **python** please use the [Google Styles Guide](https://google.github.io/styleguide/). (as much as you can :D)

## Contribution
Just fork and submit PR.  
Any PR is welcomed as long as it meets the requirements described in the above sections.

## License
All of the codebases are **MIT licensed** unless otherwise specified.
