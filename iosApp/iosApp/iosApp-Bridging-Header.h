//
//  iosApp Bridging Header
//
//  Bridges Objective-C(++) classes (currently MoonRenderer.mm wrapping
//  Filament's C++ API) into the Swift code in this target. See
//  ai-docs/decisions/0002-filament-ios-distribution.md §"Why this works
//  without cinterop on iosApp/ headers" — the Filament `<filament/...>`
//  includes stay inside MoonRenderer.mm; Swift only sees the ObjC interface
//  in MoonRenderer.h.
//

#ifndef iosApp_Bridging_Header_h
#define iosApp_Bridging_Header_h

#import "MoonRenderer.h"

#endif /* iosApp_Bridging_Header_h */
