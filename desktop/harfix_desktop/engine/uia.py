"""Asks Windows UI Automation whether the focused text box is a password
field (works in browsers, Office, Telegram and most modern apps).

Plain COM through ctypes: ctypes releases the GIL during the call, so a
slow or frozen app never delays the keyboard hook."""
import ctypes
import uuid
from ctypes import wintypes as w

ole32 = ctypes.OleDLL("ole32")
oleaut32 = ctypes.OleDLL("oleaut32")

CLSID_CUIAutomation = uuid.UUID("ff48dba4-60ef-4201-aa87-54103eef594e")
IID_IUIAutomation = uuid.UUID("30cbe57d-d9d0-452a-ab13-7ac5ac4825ee")
UIA_ProcessIdPropertyId = 30002
UIA_IsPasswordPropertyId = 30019
VT_BOOL, VT_I4 = 11, 3

# vtable slots: IUnknown 0-2, IUIAutomation.GetFocusedElement 8,
# IUIAutomationElement.GetCurrentPropertyValue 10
_RELEASE, _GET_FOCUSED, _GET_PROPERTY = 2, 8, 10


class GUID(ctypes.Structure):
    _fields_ = [("data", ctypes.c_byte * 16)]

    @classmethod
    def of(cls, u: uuid.UUID):
        g = cls()
        ctypes.memmove(g.data, u.bytes_le, 16)
        return g


class VARIANT(ctypes.Structure):
    _fields_ = [("vt", ctypes.c_ushort), ("r1", ctypes.c_ushort), ("r2", ctypes.c_ushort), ("r3", ctypes.c_ushort),
                ("value", ctypes.c_longlong), ("extra", ctypes.c_longlong)]


def _method(ptr, slot, *argtypes):
    vtable = ctypes.cast(ctypes.cast(ptr, ctypes.POINTER(ctypes.c_void_p))[0], ctypes.POINTER(ctypes.c_void_p))
    return ctypes.WINFUNCTYPE(ctypes.HRESULT, ctypes.c_void_p, *argtypes)(vtable[slot])


def _release(ptr):
    vtable = ctypes.cast(ctypes.cast(ptr, ctypes.POINTER(ctypes.c_void_p))[0], ctypes.POINTER(ctypes.c_void_p))
    ctypes.WINFUNCTYPE(ctypes.c_ulong, ctypes.c_void_p)(vtable[_RELEASE])(ptr)


class Automation:
    """Create and use in one thread (COM apartment)."""

    def __init__(self):
        ole32.CoInitializeEx(None, 0)  # COINIT_MULTITHREADED
        self.ptr = ctypes.c_void_p()
        ole32.CoCreateInstance(ctypes.byref(GUID.of(CLSID_CUIAutomation)), None, 1,
                               ctypes.byref(GUID.of(IID_IUIAutomation)), ctypes.byref(self.ptr))

    def focused_property(self, prop: int):
        """A property of the element that has keyboard focus (bool/int), or None."""
        element = ctypes.c_void_p()
        try:
            _method(self.ptr, _GET_FOCUSED, ctypes.POINTER(ctypes.c_void_p))(self.ptr, ctypes.byref(element))
        except OSError:
            return None
        if not element.value:
            return None
        try:
            v = VARIANT()
            _method(element, _GET_PROPERTY, ctypes.c_int, ctypes.POINTER(VARIANT))(element, prop, ctypes.byref(v))
            try:
                if v.vt == VT_BOOL:
                    return bool(v.value & 0xFFFF)
                if v.vt == VT_I4:
                    return ctypes.c_int32(v.value & 0xFFFFFFFF).value
                return None
            finally:
                oleaut32.VariantClear(ctypes.byref(v))
        except OSError:
            return None
        finally:
            _release(element)

    def focused_is_password(self) -> bool:
        return bool(self.focused_property(UIA_IsPasswordPropertyId))

    def close(self):
        if self.ptr.value:
            _release(self.ptr)
            self.ptr = ctypes.c_void_p()
        ole32.CoUninitialize()
