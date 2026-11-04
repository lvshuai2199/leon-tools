import json


class WeldingData:
    def __init__(self, procedure_id=0, continuous_weld_len=0, continuous_welding=False,
                 effective_front_solder_joint=False, moton_type=1):
        self.procedure_id = procedure_id
        self.continuous_weld_len = continuous_weld_len
        self.continuous_welding = continuous_welding
        self.effective_front_solder_joint = effective_front_solder_joint
        self.moton_type = moton_type
        self.back = {"joint": [], "pose": [], "tcp": []}
        self.front = {"joint": [], "pose": [], "tcp": []}
        self.front_solder_joint = {}
        self.interval_weld_len = 0
        self.is_next_continuous = False
        self.points = []

    def set_back(self, joint, pose, tcp=None):
        self.back["joint"] = joint
        self.back["pose"] = pose
        self.back["tcp"] = tcp or []

    def set_front(self, joint, pose, tcp=None):
        self.front["joint"] = joint
        self.front["pose"] = pose
        self.front["tcp"] = tcp or []

    def add_point(self, idx, joint, pose, tcp=None):
        point = {
            "idx": idx,
            "joint": joint,
            "pose": pose,
            "tcp": tcp or []
        }
        self.points.append(point)

    def set_mount_type(self):
        if self.moton_type == 1:
            self.moton_type = 2
        elif self.moton_type == 2:
            self.moton_type = 1

    def set_procedure_id(self, procedure_id):
        self.procedure_id = procedure_id

    def to_json(self):
        return json.dumps(self.to_dict())

    def to_dict(self):
        return {
            "procedureId": self.procedure_id,
            "back": self.back,
            "continuousWeldLen": self.continuous_weld_len,
            "continuousWelding": self.continuous_welding,
            "effectiveFrontSolderJoint": self.effective_front_solder_joint,
            "front": self.front,
            "frontSolderJoint": self.front_solder_joint,
            "intervalWeldLen": self.interval_weld_len,
            "isNextContinuous": self.is_next_continuous,
            "motonType": self.moton_type,
            "points": self.points
        }

    def check_class_full(self):
        print("数据已全部填充完毕")
